# ToDo приложение

Простое веб‑приложение для управления задачами (ToDo). Проект поставляется с готовой инфраструктурой и может запускаться полностью в Docker (prod) или с локальным приложением и инфраструктурой в Docker (dev).

## Используемые технологии
- Java 21, Spring Boot
- PostgreSQL 15
- OpenLDAP
- LDAP Account Manager (LAM)
- pgAdmin 4
- Docker, Docker Compose
- Redmine 5.1 (инфраструктурный сервис)

## Предварительные требования
- Установленные Docker и Docker Compose
- Установленные Java 21 и Maven 3.9+
- Файл `.env` в корне проекта

## Профили запуска
- Переменная в `.env`: `SPRING_PROFILE=prod` или `SPRING_PROFILE=dev`
- prod: приложение и вся инфраструктура запускаются в Docker
- dev: в Docker запускается только инфраструктура, приложение запускается локально

## Запуск в prod (всё в Docker)
1. В файле `.env` установить:

   ```bash
   SPRING_PROFILE=prod
   ```

2. Запустить с профилем `prod`:

   ```bash
   docker compose --profile prod up -d
   ```

3. Приложение будет доступно на порту, указанном в `.env` (переменная `SPRING_APP_PORT`). Интерфейсы управления:
   - pgAdmin: порт `PGADMIN_PORT`
   - LDAP Account Manager (LAM): порт `LAM_PORT`
   - Redmine: порт `REDMINE_PORT`
   - OpenLDAP: порты `LDAP_PORT` и `LDAP_SSL_PORT`

Остановить:

```bash
docker compose down
```

## Запуск в dev (инфраструктура в Docker, приложение локально)
1. В файле `.env` установить:

   ```bash
   SPRING_PROFILE=dev
   ```

2. Запустить инфраструктуру:

   ```bash
   docker compose up -d
   ```

   Сервис приложения из Docker не запускается, так как он привязан к профилю `prod`.

3. Запустить приложение локально:

   ```bash
   mvn spring-boot:run
   ```

   Приложение использует параметры из `.env` и подключается к контейнерам инфраструктуры.

Остановить инфраструктуру:

```bash
docker compose down