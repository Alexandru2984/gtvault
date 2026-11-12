# Audit RewardHub

Evaluare din 11 noiembrie 2026. Operator confirmat de proprietar — Dragne Alexandru Mihai, 984.alexmihai@proton.me.

## Domeniu și dovezi

Au fost inspectate codul, migrările, testele, istoricul Git, artefactul publicat, vhost-ul proiectului, configurația de încredere Cloudflare, izolarea systemd și metadatele PostgreSQL. Cererile către producție au verificat pagini și răspunsuri publice. Înscrierile, comenzile și celelalte operații de test au folosit baze izolate.

La începutul evaluării, artefactul live identificat în `/opt/gtshop` folosea Spring Boot 3.4.1, Spring Security 6.4.2 și Tomcat 10.1.34. `/magazin` răspundea cu 404, iar `/RewardHub/api/gamification/config` cu 500. Versiunea nouă a fost ulterior publicată cu Spring Boot 3.5.16, Tomcat 10.1.61 și remediile de biblioteci din repo.

Au fost găsite 3 conturi și 16 achiziții în baza live la verificarea inițială. S-a exportat schema, apoi s-au executat V2–V6 într-un PostgreSQL izolat cu un cont și o achiziție sintetice. Proprietarul aplicației avea drept de CREATE în schemă, însă 7 tabele aparțineau rolului `postgres`. Scriptul existent pentru transferul proprietății a fost inclus în exercițiul de migrare.

## Constatări și remedieri

| ID | Prioritate | Constatare | Stare |
| --- | --- | --- | --- |
| SEC-01 | P0 | Versiunea live era mult în urma controalelor de acces și migrărilor din repo | Release nou publicat și verificat pe domeniu |
| SEC-02 | P0 | Înregistrarea cu un email configurat putea acorda ADMIN fără dovada deținerii emailului | Promovarea automată eliminată din înregistrare, login și inițializare. Regresie verificată |
| SEC-03 | P1 | Originea HTTPS putea fi accesată direct prin IP, ocolind Cloudflare | Filtrul bazat pe peer-ul de origine aplicat live în vhost. Domeniul public răspunde, conexiunea directă este respinsă |
| SEC-04 | P1 | Rate limiterul putea depăși capacitatea, iar curățarea după fereastra altei rute putea elimina limite zilnice active | Capacitate strictă, ceas monotonic, ferestre păstrate per cheie și test de regresie |
| SEC-05 | P1 | Tomcat inclus de BOM avea remedieri publicate ulterior | Actualizat la 10.1.61, teste backend executate |
| SEC-06 | P1 | Inventarul Maven a semnalat versiuni afectate de Jackson, pgJDBC, Log4j API și Commons Lang | Versiunile corectate sunt fixate explicit în pom. Scanare OSV și teste repetate |
| SEC-07 | P1 | Tokenul Bearer este persistent în localStorage și rămâne accesibil scripturilor de aceeași origine | Escapare, redirecționări validate și CSP strict pregătite. Sesiunea HttpOnly și CSRF sunt în plan |
| SEC-08 | P1 | Conturile neconfirmate primesc AP care pot fi folosite pentru premii | Confirmarea emailului și eligibilitatea pentru beneficii sunt restante. Rate limitingul și limitele existente reduc numai o parte din abuz |
| SEC-09 | P2 | Redirecționările și linkurile interne puteau accepta ambiguități cu slash-uri și backslash-uri | Validare de origine și cale normalizată, teste pentru formele periculoase |
| SEC-10 | P2 | Răspunsurile întârziate ale unei sesiuni puteau actualiza indicatorii contului următor | Actualizări condiționate de tokenul cererii, resetarea stării la schimbarea contului. Verificări extinse la notificări și la emiterea tokenului după schimbarea parolei/logout-all |
| SEC-11 | P2 | Pagina de coș putea citi starea înainte de încheierea adăugării | Citirile așteaptă modificările aceleiași sesiuni. Regresie unitară și browser |
| SEC-12 | P2 | CSP live permitea scripturi inline fără nonce | CSP publicată cu nonce per răspuns și surse explicite pentru integrarea Cloudflare |
| SEC-13 | P2 | Verificarea legacy a voucherului trimite codul în query string | Interfața nouă folosește POST. GET este păstrat pentru compatibilitate, iar logarea parametrilor vechi trebuie urmărită |
| SEC-14 | P2 | Nu a fost confirmat un program complet de backup și alertare pentru acest proiect | Backup complet și restaurare izolată executate. Automatizarea, retenția și copia externă rămân de configurat |
| SEC-15 | P2 | JavaScript-ul vechi, imaginile și service workerul nu corespundeau noii interfețe | Resurse locale completate, iconuri PWA valide și fallback offline. API-urile și datele personale sunt excluse din cache-ul workerului |
| SEC-16 | P2 | CI verifica numai sintaxa fișierului JavaScript vechi | Verificări de module, rute, browser, dependențe și secrete pregătite în workflow |
| SEC-17 | P1 | Logout-ul din interfață elimina numai copia locală, iar tokenul copiat rămânea utilizabil | Sesiuni server-side legate de JWT, revocare individuală și logout server-side publicate. Verificate cu PostgreSQL izolat și în CI |
| SEC-18 | P2 | Înmulțirea procentelor cu totaluri mari putea depăși `int`, producând reduceri negative sau greșite | Calcule intermediare pe `long`, cu două teste la limitele permise pentru coș. Remediere verificată local și în CI |
| SEC-19 | P2 | Soluția Wordle era recalculată din secret și conținut, astfel încât o rotație putea schimba o rundă existentă sau grila ei | V10 salvează cuvântul zilei la prima încercare validă și leagă rundele noi de el. Verificat local, nepublicat |
| SEC-20 | P2 | Quiz-ul rezolvat era reconstruit din banca actuală; schimbarea variantelor sub același ID putea evalua altă opțiune decât cea afișată | V11 salvează rezultatul complet în tranzacția răspunsului. Formularul nou verifică amprenta variantelor vizibile. Verificat local, nepublicat |

Riscurile de bibliotecă sunt analizate după configurația aplicației. De exemplu, remediile Tomcat pentru HTTP/2, AJP sau WebSocket au condiții proprii de exploatare. Inventarul de versiuni identifică pachetele care necesită actualizare, iar atingerea unui endpoint vulnerabil se evaluează separat. [Buletine Apache Tomcat](https://tomcat.apache.org/security-10.html)

Spring Boot 3.5.16 este ultima versiune OSS a generației sale. Două avertizări Spring MVC rămân urmărite explicit în `scripts/dependency-exceptions.json`, cu revizuire până la 11 decembrie 2026. Aplicația nu folosește SSE cu fragmente de view sau `XsltView`, condițiile descrise de acele buletine. Migrarea la Boot 4 rămâne în plan. [Spring Boot 3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [SSE](https://spring.io/security/cve-2026-47890/), [XsltView](https://spring.io/security/cve-2026-47884/)

## Date și limite de încredere

Un compromis al contului de administrator poate afecta catalogul, inventarul, soldurile și toate comenzile. Un compromis al backendului poate expune emailuri, hashuri de parolă, activitatea membrilor și datele de livrare stocate. Accesul complet la VPS ar extinde incidentul la serviciile și secretele accesibile atacatorului pe gazda comună.

Rolul PostgreSQL al aplicației nu este superuser și nu poate crea roluri sau baze. Serviciul live folosește un utilizator dedicat, `NoNewPrivileges`, `ProtectSystem=strict`, `ProtectHome` și `PrivateTmp`. Portul backendului este legat de loopback.

API-ul nou are autorizare implicită, validează utilizatorul și versiunea tokenului la fiecare cerere și calculează prețurile pe server. Cererile de checkout folosesc idempotency key. Aceste controale sunt acoperite de testele de integrare, inclusiv acces între conturi, stoc, revocare și roluri. În producție, accesul anonim la API-ul de administrare răspunde cu 401.

Etapa de sesiuni adaugă verificarea identificatorului JWT în baza de date. Sunt stocate numai identificatorul aleatoriu și metadate minime despre dispozitiv, fără token Bearer, IP sau user-agent complet. Blocarea contului serializează emiterea sesiunilor și verificarea parolei față de schimbările concurente. Limita de 10 sesiuni a fost verificată prin autentificări simultane. Revocarea oprește cererile autentificate ulterior, fără a anula o operație deja autorizată și aflată în curs. Migrarea V7 cere reautentificarea tokenurilor emise înaintea acestei etape.

Preferințele de notificare sunt legate exclusiv de contul autentificat. Atât emisiile individuale, cât și inserările în masă pentru favorite și anunțuri țin cont de opțiunile salvate. Comenzile, soldurile, cadourile, recomandările și deciziile de moderare rămân active. Migrarea V8 este aditivă și păstrează alertele existente. Preferințele sunt incluse în exportul de date și se elimină prin cascadă la ștergerea contului.

Migrarea V9 adaugă un singur obiectiv de economisire per cont. API-ul folosește exclusiv identitatea autentificată și aceeași logică de preț ca checkout-ul, inclusiv limitele ofertelor per utilizator. Obiectivul nu acordă AP, nu rezervă stoc și nu declanșează achiziții. Verificările alertelor blochează contul pentru a preveni duplicatele; loturile sunt limitate și parcurg obiectivele după ultima verificare. Alegerea și ștergerea sunt limitate împreună la 30 de operații pe zi. Exportul și ștergerea contului includ noua stare.

Migrarea V10 este pregătită local. Cuvântul, tema și explicația zilei sunt comune tuturor participanților și rămân stabile după prima încercare validă, inclusiv între instanțe cu chei diferite. Citirile și încercările invalide nu creează o rundă comună. Soluția nu este expusă participanților înaintea încheierii rundei proprii. Pentru victoriile vechi se poate folosi ultima încercare; pentru înfrângerile fără soluție salvată se păstrează scorul și se marchează grila ca indisponibilă.

Istoricul Wordle este privat implicit și folosește exclusiv contul autentificat. Listele sunt paginate, cu cel mult 30 de runde pe pagină. Rundele neîncheiate și cele viitoare sunt excluse, inclusiv din statisticile personale. Replay-ul este o citire, fără operații de creditare sau progres în misiuni. Lista nu include soluții sau încercări în clar; distribuirea include numai scorul și marcajele. Dialogurile Wordle se închid la schimbarea paginii ori contului, pentru a elimina rezultatele private ale sesiunii precedente.

Caietul de quiz din V11 este privat, cu paginare limitată la 20 de rezultate și filtre parametrizate în SQL. Soluțiile și variantele nu sunt incluse în liste; detaliul cere un răspuns propriu deja trimis. Statisticile și detaliile exclud datele viitoare. Salvarea folosește aceeași tranzacție și blocare a contului ca recompensa zilnică. Citirile și exercițiile de recapitulare nu acordă AP sau progres în misiuni. Datele intră în export și se elimină prin cascadă la ștergerea contului. Cererile și dialogurile paginii de activități sunt legate de sesiunea care le-a deschis, pentru a evita afișarea rezultatelor în contul schimbat între timp.

CSP permite scripturile proprii, nonce-uri generate per răspuns și beaconul Cloudflare deja folosit pe domeniu. Paginile HTML răspund cu un document proaspăt pentru a păstra potrivirea dintre nonce și scripturile inserate de Cloudflare. Reîncărcarea paginilor a fost verificată în browser. [Integrarea CSP cu JavaScript Detections](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/javascript-detections/)

## Model de amenințări

| Amenințare STRIDE | Scenariu prioritar | Probabilitate estimată | Impact |
| --- | --- | --- | --- |
| Spoofing și elevation of privilege | Cont administrativ obținut fără confirmarea identității sau sesiune furată prin script | Ridicată înainte de remedierea promovării | Ridicat |
| Tampering și repudiation | Modificări de sold, comenzi sau stoc, inclusiv cereri concurente | Medie | Ridicat |
| Information disclosure și DoS | Date private expuse printr-un control de acces lipsă sau epuizarea resurselor API | Medie | Ridicat |

Acestea sunt estimări calitative pentru prioritizare. Pierderea anuală estimată și costul unui incident nu pot fi calculate credibil fără valoarea premiilor, costul operațional și frecvențe măsurate.

## Detectare și răspuns

Timpul actual până la detectare nu a fost măsurat. Pentru acest proiect nu a fost identificată o regulă completă cu alertă și responsabil confirmat. Operatorul de contact este cunoscut, iar rotația de intervenție rămâne de stabilit.

Regulile propuse urmăresc un obiectiv de alertare de 5 minute pentru indisponibilitate și 15 minute pentru abuz de cont sau economie. Pragurile se calibrează după trafic.

- Răspunsuri 5xx repetate sau health indisponibil în trei verificări consecutive.
- Creșterea respingerilor de autentificare și a răspunsurilor 429.
- Ajustări administrative de AP și schimbări ale rolurilor în afara unei intervenții cunoscute.
- Solduri sau stocuri incompatibile cu registrul și comenzile confirmate.
- Eșecul copiei de siguranță sau al verificării restaurării.

Procedura de intervenție este în `deploy/README.md`. Exercițiile executate au verificat migrarea schemei reale cu date sintetice, funcționarea noii aplicații și restaurarea unui dump complet de producție. Exercițiul complet de răspuns la compromitere rămâne de executat.

## Confidențialitate și furnizori

Datele implicate includ informații de cont, activitate, conținut comunitar și adrese de livrare. Operatorul și contactul au fost adăugate în interfață după confirmarea proprietarului. Retenția jurnalelor, backupurilor și comenzilor, temeiurile de prelucrare și relațiile cu furnizorii necesită completare.

Pentru incidentele care îndeplinesc condițiile articolului 33 GDPR, notificarea autorității se face fără întârzieri nejustificate și, când este fezabil, în cel mult 72 de ore de la luarea la cunoștință. Comunicarea către persoanele afectate are condițiile distincte din articolul 34. [Regulamentul UE 2016/679](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng)

Furnizorii observați sunt Cloudflare, găzduirea VPS și GitHub pentru surse/CI. Nu au fost adăugați furnizori de email, plăți sau curierat. Contractele de prelucrare și evaluările furnizorilor nu au fost puse la dispoziție pentru verificare.

## Rezultate și limite

- Suita backend completă a trecut 169 de teste pe Java 21 și 25 în GitHub Actions, inclusiv sesiunile, obiectivul de economisire și limitele numerice ale prețurilor. Suita locală pe Java 25 a trecut aceleași teste.
- Testele frontend acoperă escapare, linkuri, CSV și concurența dintre adăugare și citirea coșului.
- Browserul verifică fluxuri reale prin nginx și PostgreSQL izolat, inclusiv CSP, rutare, acces, administrare și offline.
- Gitleaks a scanat istoricul Git disponibil. Cele 14 potriviri inițiale erau parole sintetice din teste. Excepția este restrânsă la acele valori și căi de test.
- Inventarul runtime a fost verificat prin OSV. Excepțiile documentate se referă numai la condițiile Spring MVC descrise mai sus.
- Backupul complet realizat înaintea publicării a fost restaurat într-un container eliminat la final. Cele 3 conturi, 16 achiziții și 12 premii au fost verificate. Fișierul este accesibil numai root în `/var/backups/gtshop/gtshop-predeploy.dump`. Copia se află pe același VPS.
- CI a trecut pe GitHub pentru Java 21 și 25, inclusiv testele de browser și scanările. După publicare, domeniul a fost verificat în Chromium. O permisiune prea restrictivă a directorului frontend și integrarea nonce-urilor Cloudflare au fost corectate în timpul verificării publicării.
- Actualizarea V7–V8 a păstrat integral datele existente în 22 de tabele. Dumpul curent a fost restaurat și comparat înainte de migrare. Au rămas 3 conturi, 16 achiziții, 16 comenzi și 12 premii. Directorul de backup existent a fost reutilizat fără subdirectoare noi.
- Parserul inventarului Maven elimină secvențele ANSI înainte de scanare și are două teste de regresie. OSV verifică 95 de biblioteci runtime. Două avertizări condiționale rămân documentate, fără constatări noi nerevizuite.
- Extinderea testului de browser a produs răspunsuri 429 pe runnerul rapid. Testul navighează prin interfață și își limitează cererile automate la un ritm sub plafonul nginx. Limitele aplicației au rămas active. Verificarea ulterioară a trecut integral în CI.
- Etapa Wordle nepublicată a trecut local 181 de teste backend și 7 teste JavaScript. Browserul verifică o rundă încheiată, filtrarea, replay-ul, copierea fără soluție, lipsa derulării orizontale la 320 px și schimbarea efectivă a contului din alt tab, care închide dialogul privat. Nu a fost făcut push pentru aceste modificări.
- Etapa caietului de quiz a trecut local 189 de teste backend și 7 teste JavaScript. Cele opt teste noi verifică izolarea conturilor, schimbarea conținutului, paginarea și filtrele, trimiterea concurentă, exportul și ștergerea. Browserul verifică rezultatul păstrat după reîncărcare, recapitularea fără cereri de recompensă și schimbarea contului atât în timpul trimiterii, cât și cu dialogul deschis. Interfața a fost verificată la 320, 390, 768 și 1440 px. Ultima rulare GitHub existentă pentru `d66c3c8` este verde; etapa nouă nu a fost trimisă pe remote.

Testele de încărcare și extinderea auditului la celelalte aplicații de pe VPS rămân de planificat. Prioritățile restante sunt protecția identității, backupurile periodice și alertarea.
