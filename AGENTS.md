# Reguli pentru acest repo

- Push-ul și actualizarea ramurilor remote sunt făcute exclusiv de proprietar. Nu executa `git push` și nu actualiza referințe remote prin API sau alte instrumente.
- După fiecare etapă verificată, fă un commit local folosind identitatea Git deja configurată a proprietarului. Nu adăuga atribuiri către asistent, contributori sau linii `Co-authored-by`.
- Repo-ul se află pe VPS-ul de producție. Folosește baze izolate și date sintetice pentru teste; păstrează secretele în afara logurilor.
- Refolosește directorul existent de backup. Nu crea serii de directoare de backup și elimină temporarele proprii după verificări.
