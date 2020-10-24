# Rest_Api

mikroserwis w oparciu o bibliotekę vert.x (https://vertx.io/), maven

baza danych Mongo Db została utworzona w Docker, do kontrolowania zawartości bazy wykorzystano MongoDB Compass

autoryzacja: JWT auth

do komunikacji wykorzystano Postman:

-rejestracja : Post http://localhost:8080/register {"login":"","password":""}

-logowanie : Post http://localhost:8080/login {"login":"","password":""}

-dodanie itemu : Post http://localhost:8080/items {"name":""}

-wyswietlanie itemow uzytkownika: Get http://localhost:8080/items
