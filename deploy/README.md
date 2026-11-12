# Publicarea și operarea RewardHub

Versiunea publicată folosește JAR-ul și frontendul din `/opt/gtshop/releases/rewardhub-20261111T023316Z-930f404/`. Sursele generează `target/gt-shop-3.0.0.jar` și `target/frontend/`. Directorul existent `/var/backups/gtshop` conține fișierele de revenire ale aplicației.

## Verificare izolată

Sunt necesare Java 21 sau 25, Maven, Docker, Node 24, nginx, Python 3, curl și OpenSSL.

```bash
mvn -B verify
npm ci --ignore-scripts
npm run check:frontend
npm run test:frontend
npm run test:tooling
npx playwright install chromium
bash frontend/scripts/browser-check.sh
```

Ultima comandă pornește PostgreSQL, backendul și nginx pe porturi de loopback, creează numai conturi sintetice și verifică interfața generată. Procesele și containerul proprii sunt eliminate la final. Logurile și capturile rămân în `test-results/browser/`.

Buildul HTML pentru catalog se poate executa și cu un API pornit separat.

```bash
REWARDHUB_CATALOG_URL=http://127.0.0.1:8087/RewardHub/api/rewards npm run build:frontend
```

Datele produselor sunt un snapshot de build. După modificarea catalogului, reconstruiește paginile și sitemapul. Interfața interactivă continuă să citească stocul și prețurile curente din API.

După verificare, `bash deploy/package-release.sh` creează un pachet în `target/`, cu JAR, frontend, configurația nginx, procedura de operare, revizia Git și hashurile fișierelor.

## Configurația observată pe VPS

| Componentă | Locație |
| --- | --- |
| Serviciu | `gtshop.service`, utilizator `gtshopapp` |
| Configurație privată | `/etc/gtshop/app.env`, acces root și grupul serviciului |
| JAR publicat anterior | `/opt/gtshop/gt-shop-2.0.0.jar` |
| Release activ | `/opt/gtshop/releases/rewardhub-20261111T023316Z-930f404/` |
| Frontend publicat anterior | `/var/www/gtshop` |
| Vhost | `/etc/nginx/sites-available/gtshop` |
| API | `127.0.0.1:8087`, context `/RewardHub` |
| Bază | PostgreSQL 18, baza `gtshop`, rolul `gtshop_user` |

Secretul JWT, parola bazei și restul setărilor private se citesc din configurația serviciului. Configurația verificată avea un JWT_SECRET peste minimul cerut. Aceste valori rămân în afara release-ului și a logurilor de test.

## Prima migrare de la versiunea live

1. Pregătește release-ul într-un director nou și salvează hashurile artefactelor. Copiază JAR-ul ca fișier nou înainte de pornirea sa.
2. Verifică erorile și operațiile în curs. Activează o fereastră de mentenanță pentru API-ul acestui vhost și oprește serviciul, astfel încât backupul și migrarea să aibă un punct consistent.
3. Salvează unitatea systemd, vhost-ul, configurația privată și un dump complet al bazei în directorul existent `/var/backups/gtshop`, folosind fișierele de revenire ale aplicației. Protejează backupul și verifică restaurarea într-o bază izolată înainte de schimbarea bazei live.
4. Verifică proprietarii tabelelor, emailurile duplicate după normalizare, soldurile negative, prețurile și voucher-ele duplicate. La audit, verificările de date executate au trecut. Repetă-le pe starea curentă.
5. Execută `deploy/db/00-transfer-ownership.sql` cu rolul administrativ PostgreSQL, numai pentru tabelele acestei aplicații. La această instalare, schema live exista fără istoric Flyway. Baseline V1 și migrările V2–V6 au fost verificate cu schema exportată și date sintetice.
6. Configurează `ExecStart` către JAR-ul nou din directorul release-ului și păstrează utilizatorul dedicat și izolarea systemd. Pornește aplicația și urmărește Flyway, validarea Hibernate și health pe loopback.
7. Include `rewardhub-http.conf` o singură dată în contextul `http` al nginx. În vhost-ul HTTPS al proiectului, folosește `rewardhub-site.conf` și un `root` către `target/frontend` din release. Păstrează setările TLS și ACME ale domeniului. Înlocuiește vechile locații ale proiectului când integrezi noul fișier.
8. Verifică `nginx -t`, apoi publică vhost-ul și resursele împreună. Confirmă catalogul, înscrierea/autentificarea cu un cont controlat, accesul privat, comenzile existente și răspunsurile pentru căi lipsă.
9. Confirmă accesul prin Cloudflare, respingerea originii directe și starea API-ului. Încheie mentenanța după verificări.

În serviciul actual există un `ReadOnlyPaths=/opt/gtshop`. Dacă release-ul este instalat în alt director, ajustează și calea disponibilă serviciului. Directorul `frontend/` din surse poate servi pentru dezvoltare, iar publicarea folosește directorul generat pentru metadatele HTML.

Fișierul `rewardhub-site.conf` depinde de variabila `$from_cloudflare_origin`, definită de configurația de încredere Cloudflare deja prezentă pe VPS. Aceasta verifică peer-ul conexiunii înainte de rescrierea IP-ului real. Testul izolat definește propria regulă numai pentru loopback. Buildul setează accesul de citire și traversare pentru procesul nginx, inclusiv când este executat cu umask restrictiv.

## Actualizări în directorul existent

La cererea operatorului, actualizările ulterioare reutilizează directorul release-ului și `/var/backups/gtshop`. Păstrează un singur set de fișiere de revenire pentru actualizarea curentă, fără directoare suplimentare de backup. Cu serviciul oprit, înlocuiește JAR-ul prin rename al unui fișier nou, păstrând copia versiunii precedente în directorul de backup. Păstrează frontendul precedent într-o singură arhivă protejată și publică buildul complet verificat, cu directoare 0755 și fișiere 0644.

Pentru V7–V8, registrul de sesiuni și preferințele sunt tabele noi. Înainte de publicare verifică restaurarea dumpului curent și păstrarea conturilor, soldurilor, comenzilor și catalogului. Închide scrierile API pe durata înlocuirii și migrării. Verifică health, inventarul Flyway, hashurile și accesul public înainte de a considera actualizarea încheiată.

## Revenire în caz de eșec

Pentru un eșec înainte de redeschiderea API-ului, păstrează mentenanța, oprește aplicația nouă și restaurează baza, unitatea și vhost-ul din același punct de backup. Verifică aplicația precedentă înainte de redeschidere.

După reluarea scrierilor, restaurarea unui dump vechi poate pierde comenzi sau activitate nouă. În acest caz, conservă starea curentă și alege remedierea înainte sau reconcilierea explicită a datelor. Revenirea numai la JAR-ul anterior nu inversează migrările Flyway.

## Administratori

Înregistrarea creează un cont USER. După verificarea identității persoanei și a contului exact, operatorul poate acorda rolul printr-o sesiune administrativă PostgreSQL. În aceeași intervenție se incrementează `token_version` și se consemnează motivul și identificatorul contului. Revocarea folosește aceeași procedură. Emailul declarat la înscriere nu acordă privilegii.

## Sesiuni revocabile

Migrarea V7 adaugă registrul de sesiuni. JWT-urile emise anterior nu au o intrare în registru și necesită autentificare nouă după publicare. Fiecare cont poate avea maximum 10 sesiuni active. O autentificare suplimentară o închide pe cea mai veche. Din setările contului se pot verifica dispozitivul estimat, ultima utilizare și expirarea, apoi închide individual celelalte sesiuni.

Logout-ul confirmat revocă sesiunea curentă în baza de date. Schimbarea parolei și deconectarea celorlalte dispozitive invalidează toate tokenurile anterioare și emit o sesiune nouă pentru dispozitivul curent. O cerere deja autorizată înaintea revocării poate încă termina operația în curs. Metadatele sesiunilor expirate sunt eliminate zilnic. Lista nu păstrează IP-uri sau user-agent complet.

## Obiectivul de economisire

Obiectivul de economisire folosește tabelul adăugat de V9, cu maximum un premiu țintă per cont. Verificarea alertelor rulează la intervale de 60 de secunde, în loturi de maximum 100, cu tranzacție separată per cont. Configurația `rewardhub.goals-alerts-delay-ms` schimbă intervalul; testele folosesc un interval mare și declanșează verificările explicit. Alerta respectă categoria de progres și se trimite o singură dată pentru selecția curentă. Selectarea repetată a aceluiași premiu păstrează această stare. Schimbarea sau eliminarea obiectivului nu afectează AP, inventarul ori comenzile.

La actualizare, reload-ul nginx este asincron. Confirmă răspunsul de mentenanță în mai multe cereri consecutive înainte de oprirea backendului și crearea dumpului consistent.

## Wordle — modificări locale pentru V10

V10 adaugă cuvintele zilnice salvate și o referință opțională pe rundele personale. Coloanele existente ale rundelor sunt păstrate. O victorie veche se poate reconstrui din ultima încercare; o înfrângere fără soluție salvată păstrează scorul, fără a inventa marcajele. Publicarea acestei migrări este încă în așteptare. La verificarea conservării datelor, compară coloanele existente ale `wordle_games` separat de noua coloană `puzzle_date`.

Istoricul personal este disponibil prin `GET /api/games/wordle/history` și `GET /api/games/wordle/history/{date}`. Ambele cer autentificare, se limitează la rundele proprii încheiate și exclud datele viitoare. Listele au maximum 30 de intrări; accesul la istoric și replay împarte o limită de 60 de cereri pe minut per cont. Replay-ul nu acordă din nou AP sau progres în misiuni. Exportul personal include data ultimei actualizări și referința rundei, fără soluția unui joc neterminat.

## Răspuns la incident

1. Notează momentul constatării, simptomele, versiunea și operațiile cunoscute. Conservă logurile și copiile existente cu acces restrâns.
2. Limitează ruta sau contul afectat. Pentru un compromis activ al aplicației, păstrează API-ul în mentenanță până la stabilirea extinderii incidentului.
3. Revocă sesiunile afectate prin versiunea tokenului. Dacă secretul JWT sau credentialele bazei au fost expuse, rotește valorile și verifică serviciile care le folosesc.
4. Verifică rolurile, ajustările AP, comenzile, inventarul și accesul la datele personale. Compară registrul AP cu soldurile și cu operațiile autorizate.
5. Evaluează datele și persoanele afectate împreună cu operatorul. Documentează decizia și termenele de comunicare aplicabile.
6. Restaurează dintr-un punct verificat sau aplică o remediere, apoi verifică fluxurile și alertele înainte de reluarea traficului.

Pentru pregătirea unei comunicări, adună perioada incidentului, categoriile de date, numărul estimat de persoane, consecințele probabile, măsurile luate și adresa de contact `984.alexmihai@proton.me`. Transmiterea comunicărilor este o acțiune distinctă a operatorului.

Exercițiul de migrare executat în această etapă a folosit schema live și date sintetice. Separat, dumpul complet din `/var/backups/gtshop/gtshop-predeploy.dump` a fost restaurat și verificat într-un container izolat, cu aceleași 3 conturi, 16 achiziții și 12 premii. Directorul este protejat pentru root. Copia externă, programul periodic și exercițiul de răspuns la compromitere rămân în plan.
