# Plan de produs RewardHub

Actualizat la 12 noiembrie 2026.

## Starea verificată

Backendul folosește Java și Spring Boot, PostgreSQL și Flyway. Frontendul modular este în `frontend/`. Domeniul public trece prin Cloudflare și vhost-ul nginx `gtshop`.

Noua aplicație a fost publicată pe `shop.micutu.com` la 11 noiembrie 2026, după confirmarea proprietarului și trecerea CI pe Java 21 și 25. Migrarea V2–V6, cele 3 conturi, cele 16 achiziții și soldurile existente au fost verificate. Filtrarea originii, interfața și API-ul funcționează în producție.

Actualizarea următoare a publicat sesiunile revocabile și preferințele de notificare, cu migrările V7–V8. Datele celor 22 de tabele existente au fost comparate integral înainte și după pornire. Dumpul curent a fost restaurat într-un PostgreSQL fără acces la rețea. CI a trecut pe Java 21 și 25, iar domeniul public a fost verificat din nou în browser. S-a reutilizat directorul release-ului și un singur set de fișiere de revenire în directorul de backup existent.

Obiectivul de economisire și corecția reducerilor pentru totaluri mari au fost publicate cu V9. Restaurarea dumpului și păstrarea integrală a celor 24 de tabele existente au fost verificate. Au rămas 3 conturi, 16 achiziții, 16 comenzi și 12 premii. Site-ul public a trecut verificările de browser, inclusiv acțiunea de obiectiv pentru vizitatori și protejarea API-ului privat.

Următoarea etapă este pregătită local, fără push sau publicare. Include V10 pentru stabilitatea cuvântului Wordle, istoricul personal paginat, filtre și replay fără recompensare suplimentară. Verificarea locală a trecut 181 de teste backend pe Java 25, 7 teste JavaScript și fluxurile reale de browser prin nginx. Producția rămâne la V9.

## Etapa 1 · Securitate și funcționare

| Lucrare | Stare | Criteriu de acceptare |
| --- | --- | --- |
| Eliminarea promovării la ADMIN pe baza emailului declarat | Implementată | Înscrierea și autentificarea păstrează rolul USER. Rolurile se modifică explicit de operator |
| Limitarea memoriei pentru rate limiting | Implementată | Număr finit de chei, ferestre separate și teste de regresie |
| Actualizarea Tomcat și a bibliotecilor cu remedieri disponibile | Implementată | Build verificat și inventar Maven scanat |
| Închiderea accesului direct la origine | Aplicată live | Cloudflare răspunde cu 200, conexiunea directă este respinsă |
| Interfață modulară responsive | Implementată | Navigare funcțională la 320, 390, 768, 1023, 1024 și 1440 px |
| Coș sincronizat cu operațiile în curs | Implementată | Adăugarea urmată imediat de deschiderea coșului afișează produsul |
| Rute pentru administrare, ajutor și erori | Implementate | Paginile se încarcă prin navigare și acces direct |
| Migrare de la schema live | Verificată cu date sintetice | V2–V6 pornesc din schema exportată și păstrează o achiziție veche de test |
| Publicarea coordonată a aplicației noi | Executată | Backup restaurabil, migrare, health și verificări publice trecute |
| Backup complet și restaurare cu date reale | Verificate | 3 conturi, 16 achiziții și 12 premii restaurate într-un PostgreSQL izolat |
| Copii de siguranță periodice și retenție | De configurat | Copii protejate, copie externă VPS-ului și verificări automate de restaurare |

## Etapa 2 · Funcții disponibile în versiunea pregătită

- Catalog cu căutare, categorii, sortare, stoc și oferte flash.
- Pagini de produs, recenzii verificate, voturi utile și raportare.
- Favorite pe server cu alerte pentru preț și disponibilitate.
- Coș, cote de preț server-side, reduceri de nivel și coduri promo.
- Checkout cu adresă de livrare, verificarea totalului și idempotency key.
- Istoric de comenzi, vouchere, copiere, anulare eligibilă și rambursare.
- Streak, freeze, quiz, Wordle, roată, cufere, misiuni și insigne.
- Bresle, clasamente, propuneri, voturi, notificări și recomandări.
- Profil, vizibilitate publică, schimbare parolă, export de date și ștergere de cont.
- Administrare pentru comenzi, membri, ajustări de puncte, stoc și jurnal.
- Comparație pentru maximum trei premii, cu link care păstrează selecția.
- Export CSV pentru întregul istoric, cu escaparea textului introdus de utilizatori.
- Verificator public de vouchere care trimite codul în corpul cererii.
- Instalare PWA și pagină de recuperare când conexiunea lipsește.
- Sesiuni active pe server, cu dispozitiv estimat, ultima utilizare și revocare individuală.
- Preferințe sincronizate pentru reduceri, reveniri în stoc, progres, comunitate și anunțuri.

Comparația, exportul CSV și verificatorul public au fost adăugate în această etapă. Frontendul aflat deja în lucru a fost completat și integrat cu API-urile existente.

## Etapa 3 · Identitate și încredere

1. Confirmarea adresei de email cu token cu expirare și utilizare unică.
2. Resetarea parolei cu răspuns uniform, limite de încercări și invalidarea sesiunilor.
3. MFA pentru administratori, cu coduri de recuperare și jurnal de modificări.
4. Listă de sesiuni și revocare individuală, inclusiv data ultimei utilizări — publicate și verificate. Logout-ul revocă tokenul pe server. Maximum 10 sesiuni active per cont.
5. Sesiune în cookie HttpOnly, împreună cu protecția CSRF și reguli explicite SameSite.
6. Verificarea eligibilității pentru premii costisitoare și recomandări, după activitate reală.
7. Invitații cu limite configurabile și istoric de acordare a bonusurilor.
8. Migrarea la o generație Spring Boot cu suport OSS activ.

Serviciul de email și regulile pentru acordarea premiilor vor fi alese înainte de activarea funcțiilor care depind de ele. Confirmarea prin email trebuie legată de accesul la beneficii, altfel conturile multiple pot exploata în continuare bonusul inițial.

## Etapa 4 · Motive să revii

| Funcție | Comportament propus |
| --- | --- |
| Obiectiv de economisire | Implementat și verificat. Premiu țintă pe server, estimare prin calculul checkout, progres AP și o alertă per selecție când devine eligibil |
| Provocări săptămânale | Obiective cu început și sfârșit, progres verificat și o singură revendicare |
| Sezoane de clasament | Perioade arhivate, premii și reguli publice |
| Trasee de învățare | Colecții de quiz-uri pe Java, frontend, Python și DevOps |
| Explicații după quiz | Caiet privat implementat local, nepublicat: întrebări și alegeri salvate, filtre pe categorie și greșeli, statistici și recapitulare interactivă fără AP. Resursele autorilor rămân de adăugat |
| Istoric Wordle | Implementat local, nepublicat. Paginare, filtre, replay pentru rundele încheiate și distribuire cu scor și grilă fără litere |
| Provocări de breaslă | Țintă comună și contribuții personale verificabile |
| Preferințe de notificare | Implementată selectarea a cinci categorii de alerte în aplicație. Agregarea periodică rămâne de evaluat |
| Colecții de premii | Liste tematice și recomandări explicate prin preferințe explicite |
| Alerte de obiectiv | Notificări când premiul țintă revine în stoc sau intră în ofertă |

Fiecare recompensă nouă va avea plafon, reguli de eligibilitate și protecție la cereri simultane. Indicatorii de produs vor urmări activarea, revenirea la 7 zile și comenzile finalizate, cu date agregate.

Obiectivul se alege din pagina produsului sau din Favorite și apare și pe pagina principală. Se păstrează pe server, se poate elimina și nu rezervă AP sau stoc. Prețul include oferta pentru care contul este eligibil și reducerea de nivel, fără cod promo. Alertele țin cont de stoc, nivel, premiile deja deținute și preferința de progres. Verificarea periodică procesează maximum 100 de obiective într-un lot, cu prioritate pentru cele neverificate de mult timp.

Caietul de quiz păstrează numai răspunsurile trimise după activarea V11. Întrebarea, variantele, alegerea și explicația rămân cele de la rezolvare, inclusiv după actualizarea conținutului. Utilizatorul poate filtra greșelile pe categorii și relua local întrebarea, fără schimbarea rezultatului sau acordarea altor AP. Răspunsurile vechi nu sunt reconstruite din banca actuală. Formularul nou verifică și versiunea conținutului vizibil înainte de trimitere.

## Etapa 5 · Magazin și comunitate

- Editor de produse în interfața de administrare, inclusiv imagini, activare și limite de comandă.
- Editor de oferte și coduri promo, cu previzualizarea prețului rezultat.
- Cozi de moderare pentru recenzii și propuneri, cu motiv și istoric.
- Anunțuri către membri, cu previzualizare și confirmare a destinatarilor.
- Căutare și filtrare în istoricul comenzilor, cu legături către produs — implementate și verificate local, nepublicate. Căutare după ID și numele istoric al produsului, status, livrare, perioadă și sortare; rezumate fără adrese sau vouchere.
- Repetarea unei comenzi printr-o cotă nouă de preț și stoc.
- Rezervări de stoc cu expirare, pentru a limita blocarea inventarului.
- Instrucțiuni de livrare specifice fiecărui premiu digital.
- Integrare reală cu furnizorul de transport ales și actualizări verificate de status.
- Întrebări despre produse, cu raportare, moderare și limite de publicare.
- Listă publică de funcții cerute și statusul deciziilor de produs.
- Exporturi administrative limitate pe rol, cu jurnal și minimizarea datelor.

## Etapa 6 · Calitate, SEO și CI

Verificarea locală include backendul cu PostgreSQL real, teste pentru securitatea frontendului și fluxuri de browser prin nginx. Workflow-ul pregătit rulează pe Java 21 și 25, folosește permisiuni GitHub de citire și versiuni de Actions fixate prin SHA.

Buildul frontend generează HTML pentru catalog și produsele active, titluri și canonical pentru paginile publice, sitemap și robots. Metadatele pentru paginile private sunt completate prin `X-Robots-Tag` și aplicație. Paginile generate trebuie reconstruite când se schimbă catalogul.

De continuat după publicare cu măsurători din trafic real.

Stările de selecție din catalog, favorite, comparație, istoric, clasament, comunitate și profil, precum și validarea codului promo, sunt corectate local. Valorile ARIA sunt explicite, astfel încât stilurile active și cititoarele de ecran primesc aceeași stare. Browserul verifică filtrele după reîncărcare, culorile selecției, taburile și valorile atributelor pe paginile vizitate.

- Core Web Vitals pe mobil și bugete pentru resursele statice.
- Navigare completă din tastatură, contrast și verificări cu cititoare de ecran.
- Teste de browser suplimentare în Firefox și WebKit.
- Search Console, sitemap trimis și verificarea paginilor indexate.
- Teste de încărcare pe un mediu izolat și verificarea limitelor poolului DB.
- Alerte pentru erori 5xx, autentificări respinse, acțiuni administrative și discrepanțe în registrul AP.
- Politici de retenție pentru loguri, backupuri și datele asociate comenzilor.

## Reguli de livrare

Fiecare etapă verificată are commit separat, cu identitatea Git existentă a utilizatorului. Mesajele nu includ coautori sau atribuire către asistent. Funcțiile externe se livrează după alegerea serviciului și configurarea credentialelor sale.

Push-ul este făcut exclusiv de proprietar. Modificările noi rămân în commituri locale, fără actualizarea ramurilor remote prin Git sau API.
