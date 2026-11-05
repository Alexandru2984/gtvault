# 🛍️ RewardHub — Magazin & Comunitate Generația Tech

[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9-red.svg?style=flat-square&logo=apachemaven)](https://maven.apache.org/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blueviolet.svg?style=flat-square&logo=githubactions)](https://github.com/Alexandru2984/gtvault/actions)
[![Production](https://img.shields.io/badge/Production-shop.micutu.com-success.svg?style=flat-square)](https://shop.micutu.com)

**RewardHub** este platforma oficială de gamification și recompense a comunității **Generația Tech**. Utilizatorii și cursanții își pot valorifica punctele de activitate (AP) obținute prin participare, rezolvarea de quiz-uri zilnice și menținerea unui daily streak activ, transformându-le în premii fizice, cursuri și vouchere digitale exclusive.

---

## 🏛️ Arhitectură & Tehnologii

```
[ Client / Browser ] (Desktop & Mobile)
         │
         ▼  HTTPS (Port 443)
[ Cloudflare Tunnel (`cloudflared`) ]
         │
         ▼  Reverse Proxy
[ Nginx (127.0.0.1:443 / SSL) ]
   ├── /var/www/gtshop/             ──> [ Frontend Static: HTML5 / Modern CSS / Vanilla JS ]
   └── /RewardHub/api/ (Proxy Pass) ──> [ Spring Boot 3.4.1 (Java 21) on Port 8087 ]
                                                │
                                                ▼  JPA / Hibernate (DDL validate)
                                       [ PostgreSQL Database `gtshop` ]
```

### Stack Tehnic
- **Backend:** Java 21 LTS, Spring Boot 3.4.1, Spring Data JPA, Spring Security, JWT (RFC 7519), OpenAPI 3 / Swagger.
- **Bază de date:** PostgreSQL 16 (tranzacții atomice, pessimistic locks `SELECT FOR UPDATE`).
- **Frontend:** Vanilla JavaScript (ES6+ modular, zero framework bloat), Canvas Confetti, SweetAlert2, CSS3 Flexbox & CSS Grid, Mobile Bottom Navigation bar.
- **Infrastructură:** Ubuntu Linux VPS, Systemd (`gtshop.service`), Nginx cu proxy loopback, Cloudflare Tunnels (`cloudflared`).
- **CI / CD & Calitate:** Maven 3.9, JUnit 5, Mockito, GitHub Actions CI Pipeline.

---

## ✨ Funcționalități Cheie

### 1. 🛍️ Catalog de Recompense & Coș de Cumpărături
- **3 Categorii Principale:** Premii fizice (tricouri, accesorii, stickere), Cursuri / Mentorat și Premii digitale.
- **Filtrare dinamică & Căutare:** Căutare debounced în timp real după nume, descriere sau categorie.
- **Modale Detaliate:** Prezentare produs, stare stoc în timp real și buton direct de cumpărare („Cumpără acum”).

### 2. ⚡ Modul Gamification: Daily Streak & Calendar
- **Activitate Consecutivă:** Urmărește numărul de zile consecutive în care cursantul s-a autentificat.
- **Recompense Progresive:**
  - Ziua 1: **+50 AP**
  - Ziua 2: **+75 AP**
  - Ziua 3: **+100 AP**
  - Ziua 4: **+150 AP**
  - Ziua 5: **+200 AP**
  - Ziua 6: **+250 AP**
  - Ziua 7+: **+500 AP** (Bonus Săptămânal Maxim)
- **Prevenire Exploatare:** Blocare la nivel de server pe baza datei calendaristice curente.

### 3. 🧠 Tech Quiz Zilnic
- Întrebări educaționale din programare (Java, Spring Boot, Arhitectură Web, Baze de Date).
- Recompensă instantanee de **+100 AP** pentru răspunsul corect, însoțit de explicație detaliată.
- O singură încercare pe zi calendaristică.

### 4. 🎡 Roata Norocului (Lucky Wheel)
- Minigame interactiv cu animație fizică realistă în CSS3 (rotație decelerată bezier).
- Cost per rotire: **50 AP**. Premii posibile: 25 AP, 50 AP, 100 AP, 200 AP, 500 AP (Jackpot) sau 75 AP.

### 5. 🏆 Clasamentul Comunității (Leaderboard)
- Podium vizual stilizat (Locul 1 👑, Locul 2 🥈, Locul 3 🥉).
- Top 10 cei mai activi cursanți ordonați descrescător după balanța de AP și zile de streak.
- Evidențiere automată pentru profilul utilizatorului autentificat.

### 6. 💖 Sistem de Favorite (Wishlist)
- Buton rapid tip inimă pe fiecare card de produs.
- Secțiune dedicată pentru vizualizarea și gestionarea produselor dorite, sincronizate local.

### 7. 🎟️ Vouchere Digitale Unice
- La finalizarea fiecărei comenzi se generează un cod de voucher digital criptografic (`GT-XXXX-YYYY-ZZZZ`).
- Afișat în istoricul de comenzi cu buton instantaneu de **Copiere în Clipboard**.

### 8. 📱 Mobile-First Responsive Design
- Bară de navigare inferioară dedicată dispozitivelor mobile (ecrane `< 768px`) cu touch target optimizat.
- Badge dinamic cu contor de produse în coș.

---

## 🔒 Audit de Securitate & Măsuri Implementate

În cadrul auditului de securitate efectuat asupra platformei, au fost identificate și remediate următoarele vulnerabilități:

| ID | Vulnerabilitate | Severitate | Descriere & Remediere |
|----|----------------|------------|------------------------|
| **SEC-01** | **Price Tampering la Checkout** | 🚨 **Critică** | Clientul trimitea prețul produselor în payload-ul JSON. **Soluție:** Prețul este acum extras exclusiv din baza de date (`reward.getPrice()`), ignorând datele furnizate de client. Cantitățile negative sau zero sunt respinse. |
| **SEC-02** | **Injectare Balanță Puncte (AP)** | 🚨 **Critică** | `/api/user/profile` permitea suprascrierea balanței prin parametrul `activityPoints`. **Soluție:** Câmpul este protejat; punctele pot fi modificate doar prin tranzacții auditate de checkout și gamification. |
| **SEC-03** | **Broken Authentication / JWT** | ⚠️ **Ridicată** | Endpoint-urile critice erau expuse fără validare de rol/sesiune. **Soluție:** `SecurityConfig` autentifică tranzacțiile, iar utilizatorul curent este rezolvat prin `SecurityContextHolder`. |
| **SEC-04** | **Nginx 403 Proxy Whitelist Block** | ⚠️ **Medie** | Nginx bloca rutele noi `/api/gamification/**` și `/api/user/**`. **Soluție:** Configurația vhost Nginx a fost extinsă cu directive sigure de proxy către portul intern `8087`. |
| **SEC-06** | **Race Condition pe Stoc (Checkout)** | ⚠️ **Medie** | Cereri simultane puteau goli stocul sub zero. **Soluție:** Implementat `findByIdForUpdate` cu **Pessimistic Write Lock** (`SELECT FOR UPDATE`). |

---

## 📡 API Reference

Toate rutele de API sunt prefixate cu `/RewardHub/api`. Documentația interactivă Swagger este disponibilă la:  
`https://shop.micutu.com/RewardHub/swagger-ui.html`

### Autentificare & Utilizatori
- `POST /api/auth/register` — Înregistrare cont nou (primește automat 1000 AP bonus de bun venit).
- `POST /api/auth/login` — Autentificare; returnează JWT Token.
- `GET /api/user/profile` — Profilul utilizatorului curent (nume, avatar, puncte, streak).
- `POST /api/user/avatar` — Schimbare avatar (URL custom sau SVG predefinit).
- `GET /api/user/history` — Istoric comenzi și coduri de vouchere generate.

### Catalog & Comenzi
- `GET /api/rewards` — Lista completă a recompenselor (suportă parametri `category` și `search`).
- `GET /api/rewards/{id}` — Detalii despre o recompensă specifică.
- `GET /api/cart` — Preluare coș curent salvat pe server.
- `POST /api/cart/add` — Adăugare produs în coș.
- `POST /api/checkout` — Finalizare comandă, deducere atomică de AP, scădere stoc și emitere voucher.

### Gamification & Comunitate
- `GET /api/gamification/status` — Status daily streak, bonus următor și disponibilitate quiz.
- `POST /api/gamification/daily-claim` — Revendicare bonus zilnic de conectare.
- `GET /api/gamification/quiz` — Preluare întrebare tehnică a zilei.
- `POST /api/gamification/quiz/submit` — Trimitere răspuns quiz (`{ "answerIndex": 0 }`).
- `POST /api/gamification/spin-wheel` — Învârtire Roata Norocului (50 AP).
- `GET /api/gamification/leaderboard` — Top 10 utilizatori ordonați după puncte.

---

## 🚀 Rulare & Dezvoltare Locală

### Cerințe de Sistem
- **JDK 21** sau mai recent
- **Apache Maven 3.9+**
- **PostgreSQL 15+**

### 1. Clonare Depozit
```bash
git clone https://github.com/Alexandru2984/gtvault.git
cd gtvault
```

### 2. Configurare Bază de Date
Creați baza de date în PostgreSQL:
```sql
CREATE DATABASE gtshop;
CREATE USER gtshop_user WITH ENCRYPTED PASSWORD 'parola_secreta';
GRANT ALL PRIVILEGES ON DATABASE gtshop TO gtshop_user;
```

Setați variabilele de mediu sau editați `src/main/resources/application.properties`:
```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/gtshop
export DATABASE_USER=gtshop_user
export DATABASE_PASSWORD=parola_secreta
export SERVER_PORT=8087
```

### 3. Rulare Teste Automate
Suita completă de teste unitare și de integrare (JUnit 5 + Mockito + H2):
```bash
mvn clean test
```

### 4. Pornire Server Local
```bash
mvn spring-boot:run
```
Accesați în browser: `http://localhost:8087/RewardHub/gtshop.html`

---

## 🛠️ Deployment în Producție

Pe serverul VPS de producție:
```bash
# 1. Build JAR de producție
mvn clean package -DskipTests

# 2. Copiere JAR în directorul de serviciu
sudo cp target/gt-shop-2.0.0.jar /opt/gtshop/gt-shop-2.0.0.jar

# 3. Copiere fișiere statice frontend
sudo cp gtshop.html app.js styles.css /var/www/gtshop/

# 4. Restart serviciu backend
sudo systemctl restart gtshop.service

# 5. Verificare stare
sudo systemctl status gtshop.service
```

---

## 👤 Autor
- **Dragne Alexandru Mihai** ([@Alexandru2984](https://github.com/Alexandru2984))
  - Email: `alex_mihai984@yahoo.com`
