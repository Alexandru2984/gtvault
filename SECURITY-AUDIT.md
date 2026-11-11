# Audit RewardHub

Evaluare din 11 noiembrie 2026. Operator confirmat de proprietar — Dragne Alexandru Mihai, 984.alexmihai@proton.me.

## Domeniu și dovezi

Au fost inspectate codul, migrările, testele, istoricul Git, artefactul publicat, vhost-ul proiectului, configurația de încredere Cloudflare, izolarea systemd și metadatele PostgreSQL. Cererile către producție au verificat pagini și răspunsuri publice. Înscrierile, comenzile și celelalte operații de test au folosit baze izolate.

Artefactul live identificat în `/opt/gtshop` folosește Spring Boot 3.4.1, Spring Security 6.4.2 și Tomcat 10.1.34. Repo-ul conține generația nouă a aplicației. La începutul evaluării, `/magazin` răspundea cu 404, iar `/RewardHub/api/gamification/config` cu 500.

Au fost găsite 3 conturi și 16 achiziții în baza live la verificarea inițială. S-a exportat schema, apoi s-au executat V2–V6 într-un PostgreSQL izolat cu un cont și o achiziție sintetice. Proprietarul aplicației avea drept de CREATE în schemă, însă 7 tabele aparțineau rolului `postgres`. Scriptul existent pentru transferul proprietății a fost inclus în exercițiul de migrare.

## Constatări și remedieri

| ID | Prioritate | Constatare | Stare |
| --- | --- | --- | --- |
| SEC-01 | P0 | Versiunea live este mult în urma controalelor de acces și migrărilor din repo | Release nou verificat izolat, publicarea completă este restantă |
| SEC-02 | P0 | Înregistrarea cu un email configurat putea acorda ADMIN fără dovada deținerii emailului | Promovarea automată eliminată din înregistrare, login și inițializare. Regresie verificată |
| SEC-03 | P1 | Originea HTTPS putea fi accesată direct prin IP, ocolind Cloudflare | Filtrul bazat pe peer-ul de origine aplicat live în vhost. Domeniul public răspunde, conexiunea directă este respinsă |
| SEC-04 | P1 | Rate limiterul putea depăși capacitatea, iar curățarea după fereastra altei rute putea elimina limite zilnice active | Capacitate strictă, ceas monotonic, ferestre păstrate per cheie și test de regresie |
| SEC-05 | P1 | Tomcat inclus de BOM avea remedieri publicate ulterior | Actualizat la 10.1.61, teste backend executate |
| SEC-06 | P1 | Inventarul Maven a semnalat versiuni afectate de Jackson, pgJDBC, Log4j API și Commons Lang | Versiunile corectate sunt fixate explicit în pom. Scanare OSV și teste repetate |
| SEC-07 | P1 | Tokenul Bearer este persistent în localStorage și rămâne accesibil scripturilor de aceeași origine | Escapare, redirecționări validate și CSP strict pregătite. Sesiunea HttpOnly și CSRF sunt în plan |
| SEC-08 | P1 | Conturile neconfirmate primesc AP care pot fi folosite pentru premii | Confirmarea emailului și eligibilitatea pentru beneficii sunt restante. Rate limitingul și limitele existente reduc numai o parte din abuz |
| SEC-09 | P2 | Redirecționările și linkurile interne puteau accepta ambiguități cu slash-uri și backslash-uri | Validare de origine și cale normalizată, teste pentru formele periculoase |
| SEC-10 | P2 | Răspunsurile întârziate ale unei sesiuni puteau actualiza indicatorii contului următor | Actualizări condiționate de tokenul cererii, resetarea stării la schimbarea contului |
| SEC-11 | P2 | Pagina de coș putea citi starea înainte de încheierea adăugării | Citirile așteaptă modificările aceleiași sesiuni. Regresie unitară și browser |
| SEC-12 | P2 | CSP live permite scripturi inline | Configurația nouă folosește scripturi locale și reguli restrictive. Necesită publicarea vhost-ului complet |
| SEC-13 | P2 | Verificarea legacy a voucherului trimite codul în query string | Interfața nouă folosește POST. GET este păstrat pentru compatibilitate, iar logarea parametrilor vechi trebuie urmărită |
| SEC-14 | P2 | Nu a fost confirmat un program complet de backup și alertare pentru acest proiect | Backup complet și restaurare izolată executate. Automatizarea, retenția și copia externă rămân de configurat |
| SEC-15 | P2 | JavaScript-ul vechi, imaginile și service workerul nu corespundeau noii interfețe | Resurse locale completate, iconuri PWA valide și fallback offline. API-urile și datele personale sunt excluse din cache-ul workerului |
| SEC-16 | P2 | CI verifica numai sintaxa fișierului JavaScript vechi | Verificări de module, rute, browser, dependențe și secrete pregătite în workflow |

Riscurile de bibliotecă sunt analizate după configurația aplicației. De exemplu, remediile Tomcat pentru HTTP/2, AJP sau WebSocket au condiții proprii de exploatare. Inventarul de versiuni identifică pachetele care necesită actualizare, iar atingerea unui endpoint vulnerabil se evaluează separat. [Buletine Apache Tomcat](https://tomcat.apache.org/security-10.html)

Spring Boot 3.5.16 este ultima versiune OSS a generației sale. Două avertizări Spring MVC rămân urmărite explicit în `scripts/dependency-exceptions.json`, cu revizuire până la 11 decembrie 2026. Aplicația nu folosește SSE cu fragmente de view sau `XsltView`, condițiile descrise de acele buletine. Migrarea la Boot 4 rămâne în plan. [Spring Boot 3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [SSE](https://spring.io/security/cve-2026-47890/), [XsltView](https://spring.io/security/cve-2026-47884/)

## Date și limite de încredere

Un compromis al contului de administrator poate afecta catalogul, inventarul, soldurile și toate comenzile. Un compromis al backendului poate expune emailuri, hashuri de parolă, activitatea membrilor și datele de livrare stocate. Accesul complet la VPS ar extinde incidentul la serviciile și secretele accesibile atacatorului pe gazda comună.

Rolul PostgreSQL al aplicației nu este superuser și nu poate crea roluri sau baze. Serviciul live folosește un utilizator dedicat, `NoNewPrivileges`, `ProtectSystem=strict`, `ProtectHome` și `PrivateTmp`. Portul backendului este legat de loopback.

API-ul nou are autorizare implicită, validează utilizatorul și versiunea tokenului la fiecare cerere și calculează prețurile pe server. Cererile de checkout folosesc idempotency key. Aceste controale sunt acoperite de testele de integrare, inclusiv acces între conturi, stoc, revocare și roluri.

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

- Suita backend completă a trecut 145 de teste după actualizarea Tomcat. Pentru endpointul nou de voucher au fost rulate suplimentar testele de comerț și securitate.
- Testele frontend acoperă escapare, linkuri, CSV și concurența dintre adăugare și citirea coșului.
- Browserul verifică fluxuri reale prin nginx și PostgreSQL izolat, inclusiv CSP, rutare, acces, administrare și offline.
- Gitleaks a scanat istoricul Git disponibil. Cele 14 potriviri inițiale erau parole sintetice din teste. Excepția este restrânsă la acele valori și căi de test.
- Inventarul runtime a fost verificat prin OSV. Excepțiile documentate se referă numai la condițiile Spring MVC descrise mai sus.
- Backupul complet din 11 noiembrie, ora 02.29 UTC, a fost restaurat într-un container eliminat la final. Numărul de conturi, achiziții și premii a coincis cu verificările live dinainte și de după dump. Fișierul este accesibil numai root în `/var/backups/gtshop/gtshop-predeploy.dump`. Copia se află pe același VPS.
- Au fost verificate Chromium și mediul Java local. Workflow-ul pregătit trebuie executat și pe GitHub după publicarea commiturilor.

Testele de încărcare și extinderea auditului la celelalte aplicații de pe VPS rămân de planificat. Prioritățile restante sunt publicarea versiunii verificate, protecția identității, backupurile și alertarea.
