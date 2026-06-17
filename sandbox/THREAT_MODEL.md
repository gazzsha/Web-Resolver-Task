# Threat Model — Sandbox Module

Документ описывает модель угроз исполнения недоверенного пользовательского кода в модуле `sandbox` системы Web-Resolver-Task. Используется для главы 3 пояснительной записки. Состояние на конец Phase 7 (sandbox harden).

## 1. Активы
- **Host-машина** (macOS dev / Linux prod) с запущенным Docker daemon.
- **PostgreSQL** с чувствительными данными: bcrypt-хеши паролей пользователей, история submissions студентов.
- **API-ключ GigaChat** (`GIGACHAT_AUTH_KEY`) и `JWT_SECRET` — в env переменных процесса worker / MainApplication.
- **JVM-процесс worker** — у него есть доступ к Docker socket и БД.

## 2. Поверхности атаки (трасса исполнения)
1. Студент → `Submission.code` → Kafka topic → `WorkerApplication`.
2. Студент → `Submission.code` → `ai-analyzer` → промпт в GigaChat.
3. Студент → `Submission.code` → `DockerSandboxService.execute` → `docker run` → исполнение в контейнере.

Доверительная граница: всё, что попадает в поле `code`, считается враждебным (RCE, prompt injection, fork-bomb, exfiltration).

## 3. Угрозы и митигации

| ID  | Угроза                                                                 | Severity | Митигация                                                                                                                | Покрыто Phase 7 |
|-----|------------------------------------------------------------------------|----------|--------------------------------------------------------------------------------------------------------------------------|-----------------|
| T1  | RCE через `Runtime.exec` / `os.system` внутри пользовательского кода   | High     | `--cap-drop=ALL`, `--security-opt=no-new-privileges:true`, `--network=none`, `--read-only` rootfs, tmpfs `noexec,nosuid,nodev` | ДА             |
| T2  | Fork-bomb / DoS на pid table host-машины (`while(true){fork();}`)      | High     | `--pids-limit=64`                                                                                                       | ДА             |
| T3  | Memory-bomb (`new int[Integer.MAX_VALUE]`)                             | Medium   | `-m <memoryLimitMb>m`, exit-code 137 → `MEMORY_LIMIT_EXCEEDED`                                                          | ДА             |
| T4  | TLE (бесконечный цикл) — заклинивание worker-потока                    | Medium   | `process.waitFor(timeoutSeconds, SECONDS)` + `destroyForcibly()`, exit-code 124 → `TIME_LIMIT_EXCEEDED`                  | ДА             |
| T5  | Сетевая exfiltration (HTTP-запрос с украденным GIGACHAT_AUTH_KEY ...)  | High     | `--network=none` отключает loopback и outbound полностью                                                                | ДА             |
| T6  | Container escape через mount Docker socket (`/var/run/docker.sock`)    | Critical | В sandbox-контейнере **socket НЕ монтируется**. Worker сам обращается к docker daemon с host. Дыра реализуется только если злоумышленник сначала пробьёт JVM worker'а (RCE на JVM), что не входит в эту модель. | ЧАСТИЧНО (см. §5)  |
| T7  | Чтение/запись host-FS через volume `/tmp/web-resolver-sandbox/<id>`     | Medium   | Каталог именуется случайным UUID (per-request), `deleteRecursively()` в `finally` после исполнения. Контейнер видит каталог как `/app`, остальной host-FS — `--read-only`. | ЧАСТИЧНО       |
| T8  | Prompt injection из кода студента в LLM (`// IGNORE INSTRUCTIONS ...`) | High     | system-prompt с фиксированной ролью + явная инструкция игнорировать команды в коде; rule-based fallback                  | Phase 6        |
| T9  | Brute-force JWT secret / подмена токена                                | Medium   | HS256 + `JWT_SECRET` ≥ 32 байт из env, не в репе                                                                        | Phase 4        |
| T10 | Path traversal через `request.code` → имя класса в shell-команде        | Low      | `extractClassName` использует regex `\w+` (`[A-Za-z0-9_]`), shell-метасимволы отфильтрованы; fallback на `request.className` | ДА             |
| T11 | Disk-fill в `/tmp/web-resolver-sandbox` (студент пишет 100 ГБ в /app)  | Low      | tmpfs внутри контейнера `size=64m`, host-bind `/app` без квоты; защита: `--rm` + cleanup в `finally`                      | ЧАСТИЧНО       |
| T12 | CPU-exhaustion на host (стерня всех ядер)                              | Low      | `--cpus <cpuLimit>` (default 1.0)                                                                                       | ДА             |

## 4. Незакрытое (out of scope MVP)

- **gVisor / nsjail / Firecracker / Kata** — отложено: macOS Docker Desktop не поддерживает user-namespace runtimes; сорвало бы 14-дневный график. Зафиксировано в DEVELOPMENT_PLAN.md.
- **Rootless docker** — оставлено для production-deploy; в dev-сборке worker запускается под обычным пользователем macOS, но Docker daemon работает с правами root.
- **Quota / disk-limit** на `/tmp/web-resolver-sandbox` (T11) — лимит не выставлен; митигация — `--rm` + `deleteRecursively()`. На практике 5–30 секунд исполнения × 64 МБ tmpfs не успеет переполнить host.
- **Seccomp custom-profile** — используется default Docker seccomp; кастомный whitelist не написан (приемлемо для MVP при `--cap-drop=ALL` + `no-new-privileges`).
- **Kernel-уязвимости** (CVE container escapes) — out of scope, патчится host-OS.

## 5. Ключевая рекомендация для production

Главная незакрытая угроза — **T6**. В MVP-схеме worker имеет прямой доступ к Docker daemon на host, что в случае RCE на JVM worker'а даёт полный container escape (worker может запустить `docker run -v /:/host ...`).

В production рекомендуется:
1. Worker деплоить как отдельный контейнер **БЕЗ** монтирования `/var/run/docker.sock`.
2. Sandbox-исполнение делегировать на отдельный VM/host (sandbox-runner) через REST/gRPC.
3. Sandbox-runner — изолированная VM (Firecracker / отдельный bare-metal) с rootless docker.

В рамках диплома (host-docker + harden) допустимо: вектор T6 защищён системными мерами (worker не принимает входящих сетевых соединений в обход auth, JWT-фильтр блокирует не-аутентифицированные запросы), в худшем случае compromise ограничен dev-машиной.
