# GT Shop Backend Integration Guide

## Overview
Acest backend servește date pentru aplicația **RewardHub** sub context path `/RewardHub` pe portul `8080`.  
Frontend-ul (JavaScript) poate consuma endpoint-urile de mai jos.

## Requirements
- Java 11+
- Maven 3.6+
- Port: **8080**

## Running the Backend
### From command line
```bash
mvn spring-boot:run

API Endpoints
1. Get User Profile
GET /api/user/profile
Full URL: http://localhost:8080/RewardHub/api/user/profile

2. List Rewards
GET /api/rewards
Full URL: http://localhost:8080/RewardHub/api/rewards

Parametri opționali:
search – text liber (caută în name/description/fullDescription)
category – una dintre: Premii fizice | Premii digitale | Premii exclusive

Exemplu: 
GET /rewards?search=Tricou
GET /rewards?category=Premii%20fizice

3. Add to Cart
POST /api/cart/add
Full URL: http://localhost:8080/RewardHub/api/cart/add

4. View Cart
GET /api/cart
Full URL: http://localhost:8080/RewardHub/api/cart

5. Checkout
POST /api/checkout
Full URL: http://localhost:8080/RewardHub/api/checkout

6. Purchase History
GET /api/user/history
Full URL: http://localhost:8080/RewardHub/api/user/history

7) History Summary
GET /user/history/summary
Full URL: http://localhost:8080/RewardHub/api/user/history/summary

# gtvault
