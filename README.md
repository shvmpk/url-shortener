# Distributed URL Shortener

A **URL shortener** — like Bitly or TinyURL. Paste a long, ugly link, get a short, shareable one back. Built to handle **millions of links** and **thousands of requests per second** without crashing.

---

## Features

### 1. Shorten URLs
Turn any long URL (e.g. `https://example.com/some/very/long/path?with=params`) into a short code like `http://localhost:8080/abc123`. The short code is generated from a unique number, **obfuscated** (scrambled using a Knuth multiplicative hash so codes aren't guessable/sequential), then encoded with **Base62** (uses characters 0-9, a-z, A-Z).

### 2. Custom Aliases
Instead of a random short code, you can pick your own name. For example, `http://localhost:8080/my-link`. The system checks that no one else is using it before assigning it.

### 3. Password Protection
Lock your short link with a password. When someone visits, they see a **password page**. If they enter the right password, they get through. If they fail **3 times within 6 hours**, they're locked out.

### 4. Auto-Generated Passwords
Don't want to think of a password? The system can generate a strong **8-character random password** for you.

### 5. "Remember Me" for Protected Links
After entering the password, visitors can check "Remember Me". The system stores an **encrypted cookie** in their browser so they don't have to re-enter the password for 7 days. The cookie is encrypted so nobody can tamper with it.

### 6. Expiration Control
You can make short links expire in two ways:
- **Time-based**: Auto-expires at a specific date/time (e.g., "expires at 2026-08-01 3:00 PM")
- **Click-based**: Auto-expires after a certain number of total clicks (e.g., "expires after 1000 clicks")

If neither is set, the link expires in 1 day by default. You can only pick one type of expiration.

### 7. QR Code Generation
Generate a QR code for any short link. Supports multiple formats:
- **PNG** — standard image format
- **JPG / JPEG** — smaller file size, good for printing
- **SVG** — vector format (scales to any size without losing quality)
- **Base64** — encoded text for embedding directly in web pages (no separate image file needed)

You can also customize the QR code: change its **color**, add **overlay text** on the bottom, or place a **logo** in the center.

### 8. URL Version History (Like "Git for URLs")
Every time you update a short link (change the destination URL, alias, password, expiration, etc.), the old state is **automatically saved as a version**. You can:
- **View all versions** — see the complete history of every change
- **Compare two versions** — see exactly what changed between them (field-by-field difference)
- **Rollback** — revert to any previous version with one click
- **Delete specific versions** — remove unwanted history entries

You never lose track of what a short link used to point to. And rollbacks themselves are recorded as new versions, so you can undo a rollback too.

### 9. Detailed Analytics
Every time someone visits a short link, the system captures:
- **IP address** (where the request came from)
- **User-Agent** (what browser and device they used)
- **Referrer** (which website they came from)
- **UTM tags** (marketing campaign parameters: utm_source, utm_medium, utm_campaign, utm_term)

The analytics service then enriches this data with:
- **Geographic location** — country, city, region, continent, latitude/longitude
- **Network info** — ASN (Autonomous System Number like "AS15169"), ISP (Internet Service Provider like "Google LLC"), organization name
- **Device type** — is it a desktop, mobile, tablet, TV, or game console?
- **Browser** — Chrome, Firefox, Safari, Edge, etc.
- **Operating system** — Windows, macOS, Android, iOS, Linux, etc.
- **Bot detection** — is the visitor a real human or a crawler/robot?

All analytics are **rolled up per day** — you see total visits for each short code per day, broken down by browser, device type, and OS. Real-time metrics like "clicks in last 10 minutes" and "clicks in last hour" are also tracked.

### 10. Unique Visitor Counting
Not all visits are equal. The system tracks **unique visitors** using browser cookies. If the same person visits 100 times, it counts as **1 unique visitor**. This gives you a true measure of how many different people are clicking your link. The visitor ID is hashed (SHA-256) for privacy before being stored.

### 11. Duplicate URL Detection
If someone tries to shorten a URL that's already been shortened, the system recognizes it and returns the existing short code instead of creating a duplicate. It does this in **constant time** using an MD5 hash of the URL stored in Redis cache. If the protection settings (password) differ, it raises a conflict error rather than silently returning the old code.

### 12. URL Validation
Before creating a short link, the system verifies:
- The URL is **properly formatted** (valid syntax, proper protocol)
- The URL is **reachable** (actually accessible on the internet — makes a HEAD request)

This prevents broken or invalid links from being saved.

### 13. Bloom Filter (Performance Booster)
A **Bloom filter** is a special data structure stored in Redis that can quickly tell us "this short code definitely does NOT exist" — without needing to check the database or cache. It has a tiny **1% chance of false positives** (saying something exists when it doesn't), but **zero chance of false negatives** (it never misses a real code). For invalid short codes, this saves a full database query. Configured for **10 million entries**.

### 14. Cache Stampede Protection
When a popular short link is accessed by many people at the exact same moment and it's not yet in the cache, the system uses a **distributed lock** (via Redis SETNX command) so only **one request** goes to the database. The other concurrent requests wait a few milliseconds and then read from the cache once it's populated. This prevents the database from getting overwhelmed by a sudden traffic spike.

### 15. Rate Limiting
Each API endpoint has a **rate limit** (max requests per minute per IP address):
- URL shortening: 1,000 per minute
- QR code generation: 500 per minute
- Analytics queries: 500 per minute
- Password verification: 2,000 per minute
- Redirects: 100,000 per minute

When exceeded, you get a `429 Too Many Requests` response with a `Retry-After` header. Uses Redis for distributed counting (works across multiple instances).

### 16. Circuit Breakers
If any backend service (database, Redis, Kafka) becomes slow or unresponsive, the **circuit breaker** trips and returns a friendly error immediately instead of making users wait for a timeout. It opens after 50% of the last 10 requests fail, stays open for 10 seconds, then tries again (half-open state with 3 test requests). This keeps the system responsive even when dependencies fail.

### 17. Distributed Tracing (Jaeger)
Every single request is **traceable** across all services. Using Jaeger (a distributed tracing tool), you can follow a request's complete journey: API Gateway → Redirect Service → Redis → PostgreSQL → Kafka. You can see exactly how long each step took, making it easy to find performance bottlenecks.

### 18. Monitoring Dashboards (Prometheus + Grafana)
Real-time metrics are collected via Prometheus and visualized in Grafana:
- Request rates and latencies per service
- Error rates and error types
- Cache hit/miss ratios
- Rate limit hits
- Kafka producer/consumer health
- Database connection pool usage
- JVM memory and garbage collection stats

A pre-built Grafana dashboard is included — just open Grafana at `http://localhost:3000`.

### 19. Log Aggregation (Loki + Alloy)
All services output structured JSON logs. These are automatically collected by **Alloy** (a log scraper that reads Docker container logs) and sent to **Loki** (a log storage system designed for Prometheus-style label-based searching). You can search and filter all logs across all services from the Grafana interface — no more `docker logs` on individual containers.

### 20. Health Probes
Every service exposes health endpoints for orchestration and monitoring:
- `/actuator/health/liveness` — is the service alive? Used by Docker/Kubernetes to know when to restart a container
- `/actuator/health/readiness` — is the service ready to accept traffic?
- `/actuator/health` — overall health summary including database, Redis, and Kafka connectivity

### 21. Consistent Error Responses
All services return errors in the same JSON format:
```json
{
  "timestamp": "2026-07-05T12:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Short code not found: abc123",
  "path": "/api/v1/urls/abc123",
  "traceId": "a1b2c3d4e5f6"
}
```

Every response includes a `traceId` that can be cross-referenced in Jaeger for debugging.

### 22. Graceful Degradation (Fallbacks)
When a service is down or slow, the API Gateway has pre-configured **fallback responses** for each service. For example:
- If the Shortener Service is down: "Shortener service is temporarily unavailable"
- If the Redirect Service is down: "Redirect service is temporarily unavailable"
- If the Analytics Service is down: "Analytics service is temporarily unavailable"

Users never see raw error pages or connection timeouts.

---

## High-Level Design (HLD) — Step by Step

Imagine the system as a **factory with 4 departments**, each doing one specific job. They communicate through **hallways** (network calls) and use shared **storage rooms** (databases, caches, message queues).

### The Big Picture

```
                    ┌──────────────────────────────────────────────────┐
                    │               API GATEWAY (:8080)                │
                    │  Rate Limiting │ Circuit Breaker │ CORS │ Auth   │
                    └────┬─────────────┬──────────────┬───────────────┘
                         │             │              │
                    ┌────▼──┐    ┌─────▼─────┐  ┌────▼────────┐
                    │SHORTENER    │ REDIRECT    │  │ ANALYTICS   │
                    │SERVICE │    │ SERVICE     │  │ SERVICE     │
                    │(:8081) │    │ (:8082)     │  │ (:8083)     │
                    └──┬──┬──┘    └──┬──┬──────┘  └──┬──┬───────┘
                       │  │          │  │            │  │
              ┌────────▼──▼──────────▼──▼────────────▼──▼───────┐
              │                    REDIS                          │
              │  Cache │ Bloom Filter │ Rate Limiter │ ID Gen    │
              └───────────────────────┬──────────────────────────┘
                                       │
              ┌───────────────────────▼──────────────────────────┐
              │                  POSTGRESQL                        │
              │   short_url (shortener) │ analytics (analytics)   │
              └───────────────────────┬──────────────────────────┘
                                       │
              ┌───────────────────────▼──────────────────────────┐
              │                    KAFKA                           │
              │        Analytics Events → Batch Consumer          │
              └──────────────────────────────────────────────────┘
```

Let's walk through each part one by one.

### Department 1: API Gateway (Port 8080)

**What it does:** The single front door. Every single request hits the API Gateway first. No other service is directly accessible from the outside world. It's built with **Spring Cloud Gateway** (reactive, non-blocking).

**Why we need it:**
- Instead of clients knowing about 4 different server addresses, they only need to know 1
- Security: internal services are hidden from the internet
- We can add cross-cutting features (rate limiting, circuit breakers, CORS) in one central place
- It's the enforcement point for all policies

**What happens when a request arrives:**

1. **Routing** — The Gateway reads the URL path and forwards to the right service:
   - `/api/v1/urls/**` → Shortener Service (URL CRUD operations)
   - `/api/v1/qr/**` → Shortener Service (QR code generation)
   - `/{segment}/verify` → Redirect Service (password verification)
   - `/{segment}` (single path segment like `/abc123`) → Redirect Service (main redirect)
   - `/api/v1/analytics/**` → Analytics Service (analytics queries)

2. **Rate Limiting** — Before forwarding, a custom `RateLimitingGatewayFilterFactory` checks the request count:
   - Uses Redis key `rate_limit:<routeId>:<clientIp>` with a sliding window
   - On first request: sets key with 1-minute expiry
   - On subsequent requests: increments the counter
   - If count exceeds capacity: returns `429 Too Many Requests` with `Retry-After` header and logs a warning
   - Each route has different capacity (redirect = 100K/min, shorten = 1K/min)

3. **Circuit Breakers** — Each route is wrapped with a Resilience4j CircuitBreaker:
   - Monitors the last 10 requests; if 50%+ fail, the circuit opens
   - In open state: all requests immediately go to a fallback endpoint (`/fallback/shortener`, etc.)
   - After 10 seconds: transitions to half-open, sends 3 test requests
   - If test requests succeed: circuit closes (normal operation)
   - If test requests fail: circuit opens again for another 10 seconds
   - This prevents **cascading failures** — if one service is down, it doesn't bring down the whole system

4. **CORS** — Configurable via `CORS_ALLOWED_ORIGINS` environment variable (defaults to `*` for development). Uses WebFlux CORS configuration.

5. **Health & Metrics** — Exposes actuator endpoints at `/api/v1/actuator/*`:
   - `/health`, `/health/liveness`, `/health/readiness`
   - `/prometheus` — Prometheus metrics
   - `/gateway` — read-only gateway info
   - All other actuator endpoints are restricted

### Department 2: Shortener Service (Port 8081)

**What it does:** Creates, updates, deletes, and manages short URLs. Think of it as the "CRUD department" — it handles everything related to creating and maintaining URL mappings. Built with Spring Boot, JPA/Hibernate, and Redis.

**What happens when you shorten a URL (complete step-by-step):**

1. **Receive the request** — The `UrlManagementController.shortenUrl()` receives the `ShortUrlRequest` DTO containing:
   - `longUrl` (required) — the original URL to shorten
   - `alias` (optional) — custom short name
   - `isProtected` (optional) — password protection flag
   - `isPasswordAutoGenerated` (optional) — auto-generate password
   - `password` (optional) — raw password (not auto-generated)
   - `expiresAt` (optional) — ISO datetime for time-based expiration
   - `maxClicks` (optional) — max number of clicks

2. **Validate inputs** — Multiple validation checks via Jakarta Bean Validation and custom `@AssertTrue` methods:
   - URL must not be blank and must be valid format
   - If password protection is on and not auto-generated, password is required (min 8 chars, must include uppercase, lowercase, digit, special character)
   - Password should not be provided if URL is not protected
   - Can't set both `expiresAt` and `maxClicks` (choose one expiration type)
   - `expiresAt` must be a valid ISO datetime and in the future
   - Alias can only contain lowercase letters, numbers, or dashes
   - Alias must not be just whitespace

3. **Validate URL reachability** — The `UrlValidator` interface (implementation uses Java's `HttpURLConnection`):
   - Checks URL format is syntactically correct
   - Makes a HEAD request to the URL to verify it's actually reachable
   - If unreachable, throws `InvalidUrlException`

4. **Normalize the URL** — Standardizes the URL format so the same URL always produces the same hash:
   - Removes trailing slashes, lowercases the domain, standardizes protocol
   - The normalized version is what gets stored and checked for duplicates

5. **Check for custom alias conflicts** — If a custom alias is provided:
   - Checks that alias length ≤ 20 characters
   - Queries database: `existsByAliasIgnoreCase(customAlias)`
   - If the alias is taken, throws `UrlConflictException`

6. **Process password protection** — If `isProtected` is true:
   - If auto-generate: uses `SecureRandom` to generate an 8-character password, then hashes it with bcrypt
   - If manual: hashes the provided password with bcrypt
   - The raw password is never stored — only the bcrypt hash

7. **Process expiration**:
   - If `maxClicks` > 0: set `isClickBased = true`, no time-based expiry
   - If `expiresAt` is set: parse ISO datetime
   - If neither: default to 1 day from now

8. **Duplicate detection via cache** — Computes MD5 hash of normalized URL:
   - Cache key: `longUrl:<md5hash>`
   - Checks Redis: if found, also fetches `shortKey:<shortCode>` from cache
   - If cached data exists AND the protection settings match (`isProtected` matches), returns the existing short URL immediately (cache hit)
   - If protection settings differ, skips cache (can't return a password-protected link when an unprotected one was requested, or vice versa)

9. **Cache stampede protection** — Uses Redis distributed lock:
   - `SETNX lock:longUrl:<hash>` with 2-second TTL
   - If lock acquired: rechecks cache (in case another thread just populated it), then proceeds
   - If lock not acquired: polls cache 5 times with 50ms intervals (total 250ms)
   - If cache is populated by the lock holder during polling, returns the cached result
   - If no cache after polling: proceeds to database anyway (lock may have failed)

10. **Database duplicate check** — Queries PostgreSQL: `findFirstByOriginalUrlIgnoreCase(normalizedUrl)`
    - If found AND protection settings match: caches the result and returns it
    - If found AND protection settings differ: throws `UrlConflictException`
    - If not found: proceeds to create a new short URL

11. **Generate unique ID (batch)** — Uses Redis atomic `INCRBY 1000` command:
    - Key: `id:short-code`, seeded at 100,000,000,000 (100 billion)
    - Each call reserves **1000 IDs at once** from Redis and caches them locally
    - 999 out of 1000 calls cost **zero network** — the local cache serves them
    - Redis is hit only once per 1000 IDs (~1 write/sec at 100M DAU)
    - The high seed ensures generated short codes have a minimum length
    - This is **distributed-safe**: no matter how many instances run, `INCRBY` is atomic

12. **Checkpoint to PostgreSQL** — Before handing out any ID from a new batch, the new high-water mark is written to the `id_checkpoint` table:
    - `UPDATE id_checkpoint SET last_value = ? WHERE last_value < ?`
    - This makes ID generation **durable across Redis restarts** — if Redis resets, the counter resumes from the checkpoint, never rewinding below already-issued IDs
    - At ~1 write/sec, this synchronous Postgres write is negligible cost

13. **Obfuscate the ID** — The raw sequential ID is scrambled using a **Knuth multiplicative hash** (64-bit):
    - `obfuscated = id * 0x9E3779B97F4A7C15L` (wrapping mod 2^64)
    - The multiplier is odd, so the mapping is bijective (no collisions, reversible)
    - This prevents **sequential enumeration**: codes like `...A, ...B, ...C` become unpredictable
    - The inverse exists for debugging: `id = obfuscated * 0xF1DE83E19937733DL`

14. **Encode to short code** — Converts the obfuscated ID to a **Base62** string using characters `0-9`, `a-z`, `A-Z`:
    - Uses `Long.divideUnsigned` / `Long.remainderUnsigned` so even values that wrap negative as signed `long` are encoded correctly
    - The result is URL-safe: no special characters, all alphanumeric

15. **Save to database** — Creates a `ShortCode` entity and saves it via Spring Data JPA:
    - Contains: original URL, short code, alias, password hash, expiration, protection flags, etc.
    - Uses `@Transactional` for atomicity

16. **Create initial version** — Creates a `UrlVersion` entity with version_number = 1:
    - Snapshots all current settings: original URL, alias, isProtected, password hash, maxClicks, expiresAt
    - Linked to the ShortCode via `short_code_id` foreign key
    - Every future update will create a new version

17. **Update current version pointer** — Sets `current_version_id` on the ShortCode record to point to the newly created UrlVersion. This allows the system to quickly know which version is active without scanning.

18. **Populate caches** — Stores two Redis entries:
    - `longUrl:<hash>` → short code (with 1-day TTL) — for future duplicate detection
    - `shortKey:<shortCode>` → full ShortCode JSON (with 1-day TTL) — for fast redirect lookups

19. **Return response** — Returns a `ShortUrlResponse` containing:
    - `shortUrl` — the full short URL (e.g., `http://localhost:8080/qMvTb8`)
    - `aliasUrl` — the alias URL if set (e.g., `http://localhost:8080/my-link`)
    - `qrCodeUrl` — URL for QR code generation
    - `expiresAt` — human-readable expiration info
    - `maxClicksAllowed` — max clicks if click-based
    - `passwordProtected` — whether the link has password protection

**What happens when you update a URL (short version):**
- The `UrlManagementController.updateShortUrl()` method is called with `PUT /api/v1/urls/{shortCodeOrAlias}`
- `ShortUrlUpdateValidator.validate()` checks that values make sense
- The service: resolves the short code, checks for changes, evicts old cache entries, creates a backup version of the current state, saves new values, creates a new version, updates the current version pointer, and repopulates caches
- If no actual changes detected, returns early without creating versions

**What happens when you delete a URL:**
- Resolves the short code/alias, deletes from database, removes all cache entries (longUrl hash, shortKey, redirect, alias)

**QR Code Generation (QrController):**
- Endpoint: `GET /api/v1/qr/{urlOrAlias}` with optional query params: `size`, `color`, `overlayText`, `logoUrl`, `format`
- Constructs the full URL from short code/alias + base URL
- Uses **ZXing library** ("Zebra Crossing") to encode the URL as a QR code bitmap
- For **PNG/JPG**: generates a `BufferedImage`, optionally draws overlay text and logo, encodes with Java ImageIO
- For **SVG**: generates raw SVG XML with `<rect>` elements for each black module of the QR matrix
- For **base64**: generates PNG bytes, then Base64-encodes them into a JSON object
- Returns with appropriate `Content-Disposition` header for file download

### Department 3: Redirect Service (Port 8082)

**What it does:** Handles ALL redirects and password verification. This is the most **performance-critical** service because it handles the most traffic (every single short link visit). Built with Spring Boot, Thymeleaf (for password pages), Redis, Kafka, and Resilience4j.

**What happens when someone visits a short link (complete step-by-step):**

1. **Receive the request** — The `RedirectController.handleRedirect()` receives `GET /{shortCodeOrAlias}`:
   - The API Gateway has already checked rate limits and routed the request here
   - The path variable could be either a short code (`abc123`) or a custom alias (`my-link`)

2. **Bloom filter check** — `RedirectService.resolveShortCodeOrAlias()` first checks Redis Bloom filter:
   - Uses native Redis `BF.EXISTS` command via Lettuce (low-level Redis client)
   - Filter key: `bloom:shortcodes` — initialized with 10M capacity and 1% false positive rate
   - If Bloom filter returns 0 (definitely doesn't exist): immediately returns 404 without touching cache or database
   - If Bloom filter returns 1 (might exist): proceeds to cache lookup
   - This saves expensive cache/DB lookups for random or malicious requests (e.g., bots probing random codes)
   - On Redis connection failure: falls back to returning "true" (assume it might exist) — slightly slower but correct

3. **Cache lookup** — Checks Redis for `shortKey:<shortCodeOrAlias>`:
   - If found: deserializes the JSON to a `ShortCode` object using Jackson
   - Returns the ShortCode without touching the database
   - Cache TTL: 1 day (set by Shortener Service when creating/updating)

4. **Cache stampede protection** — If cache miss:
   - Acquires `SETNX lock:shortKey:<code>` with 2-second TTL
   - If locked: rechecks cache (double-checked locking pattern), then does DB lookup
   - If not locked: polls cache 5 times with 50ms intervals
   - If cache populated by waiter: returns cached result
   - If not: does DB lookup anyway

5. **Database lookup** — Queries PostgreSQL:
   - First: `findByShortCodeIgnoreCase(code)` — look up by short code
   - If not found: `findByAliasIgnoreCase(code)` — look up by alias
   - If found: populates cache (`shortKey:<shortCode>`) and returns the ShortCode
   - If not found by either: throws `UrlNotFoundException`
   - All database calls are wrapped with `@CircuitBreaker` and `@Retry`

6. **Password-protected check** — If `isProtected == true`:
   - Calls `checkVerification(canonicalShortCode, request)`:
     - Checks HTTP session for `verified_<shortCode>` attribute
     - Checks all cookies for `verified_<shortCode>` cookie (decrypts and verifies)
   - If already verified: renders `already-verified.html` (Thymeleaf template) with a link to the destination + a note about whether verification came from session or cookie
   - If NOT verified: renders `verify.html` (password form)
   - The password page shows the short link alias and a password input field

7. **Verify password** (`POST /{shortCode}/verify`):
   - Rate limits password attempts: max 3 attempts per 6 hours per session
   - Uses `PasswordVerifier.verify(password, storedHash)` which checks against bcrypt hash
   - On success: stores `verified_<shortCode> = true` in session
   - On success with "Remember Me" checkbox: creates a secure HTTP-only encrypted cookie with 7-day lifetime
   - On failure: increments attempt counter in session, shows error with attempt number
   - On exceeding 3 attempts: locks out for 6 hours with error message

8. **Process redirect** — `RedirectService.processRedirect()`:
   - **Check time-based expiration**: if `expiresAt` is set and `LocalDateTime.now()` is past it:
     - Evicts all cache entries for this short code (so subsequent requests fail fast)
     - Throws `UrlExpiredException` which results in a `410 Gone` page
   - **Check click-based expiration**: if `maxClicks` is set and `uniqueVisitorCount >= maxClicks`:
     - Throws `MaxClicksExceededException` which results in a `410 Gone` page
   - **Cache the redirect URL**: stores `redirect:<shortCode>` → original URL in Redis:
     - If time-based expiration: TTL = remaining time until expiry
     - If no expiration: TTL = 7 days
     - This allows the redirect to be served directly from cache on subsequent visits
   - **Track unique visitors**:
     - Reads or creates a `uid` cookie (HTTP-only, secure, 30-day lifetime, UUID v4)
     - Hashes the visitor ID with SHA-256 (one-way hash — the original ID is not stored anywhere for privacy)
     - Stores the hash in Redis Set under `visitors:<shortCode>` (30-day TTL)
     - If `SADD` returns 1 (new visitor): increments `unique_visitor_count` in PostgreSQL
     - If `SADD` returns 0 (returning visitor): does nothing

9. **Publish analytics event (async)** — `AnalyticsService.saveAnalyticsAsync()`:
   - Parses the User-Agent header using **YAUS** (Yet Another User Agent Analyzer) library with a 10,000-entry cache
   - Extracts: device type (Desktop/Mobile/Tablet/Robot), browser name, OS name, bot flag
   - Reads: referer header, UTM parameters from query string (`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`)
   - Builds an `AnalyticsEvent` with all data + current timestamp
   - Sends to Kafka via `KafkaAnalyticsProducer.sendAnalyticsEvent()`
   - Kafka producer is configured with:
     - `@Retry` (Resilience4j) — retries on transient failures
     - `acks=all` — waits for all Kafka replicas to acknowledge
     - `enable.idempotence=true` — prevents duplicate events
   - If Kafka send fails after retries: logs error and increments a Micrometer counter for monitoring

10. **Return redirect** — Returns an HTTP 302 redirect response pointing to the original URL:
    - The browser automatically follows the redirect to the destination
    - For password-protected URLs that are already verified, a link is shown on the `already-verified.html` page

### Department 4: Analytics Service (Port 8083)

**What it does:** Consumes analytics events from Kafka, enriches them with geographic data, stores them in PostgreSQL with daily rollups, and provides a query API. Built with Spring Boot, Spring Kafka, MaxMind GeoIP2, and Hibernate/JPA.

**What happens to an analytics event (complete step-by-step):**

1. **Kafka consumer receives a batch** — The `AnalyticsEventConsumer.consume()` method is a `@KafkaListener`:
   - Topic: `analytics-events`
   - Consumer group: `analytics-group`
   - Spring Kafka auto-configures batch consumption
   - The method receives a `List<AnalyticsEvent>` — Kafka delivers events in batches (configurable)

2. **Group events by short code and date** — Events are grouped using Java Streams:
   - Key: `new ShortCodeDateKey(shortCode, accessDate)` — a private record type
   - Value: `List<AnalyticsEvent>` — all events for that code + day
   - Uses `LinkedHashMap` to preserve insertion order

3. **For each group, merge events together** — `mergeGroup(shortCode, date, events)`:
   - Checks if a record already exists for this short_code + access_date in PostgreSQL
   - If exists: fetches the existing Analytics record
   - If not exists: creates a new Analytics entity with default values (zero counts, empty maps)

   For each event in the group:

   - **Total visit count**: `existing.totalVisitCount + event.totalVisitCount`
   - **Browser counts**: merges `Map<String, Integer>` — e.g., Chrome: 5 + Chrome: 3 = Chrome: 8
   - **Device type counts**: same merge pattern
   - **OS counts**: same merge pattern
   - **Browser "last seen"**: keeps the latest timestamp per browser
   - **Device "last seen"**: keeps the latest timestamp per device type
   - **Recent access times**: adds the current timestamp, removes entries older than 1 hour
   - **Clicks last 10 minutes**: counts recent access times within the last 10 minutes
   - **Clicks last 1 hour**: counts all recent access times

4. **GeoIP enrichment** — For the first event's IP address in the group:
   - Uses MaxMind's `GeoIpService.lookup(ip)`:
     - Loads two GeoLite2 databases on startup: City (geographic) and ASN (network provider)
     - On each lookup: creates an `InetAddress`, checks if it's private/local (skips those)
     - Queries City database: returns country, city, region, continent, latitude, longitude
     - Queries ASN database: returns ASN number, ISP name, organization name
     - All lookups wrapped with `@CircuitBreaker` — if database file is missing or corrupted, gracefully returns null values
   - Sets these values on the Analytics record:
     - `country`, `city`, `region`, `continent`, `latitude`, `longitude`
     - `asn`, `isp`, `organization`
   - Also sets: `referer`, `utmSource`, `utmMedium`, `utmCampaign`, `utmTerm`, `isBot`, `userAgent`, `lastAccessTime`

5. **Save to PostgreSQL** — Uses `analyticsRepository.saveAll(merged)`:
   - Batch insert is much more efficient than one-by-one saves
   - Uses `@Transactional` for atomicity
   - The table has a unique constraint on `(shortCode, accessDate)` — ensures one record per code per day
   - Micrometer Timer tracks DB save duration
   - Counter tracks total events processed

6. **Query API** — Two endpoints via `AnalyticsController`:
   - `GET /api/v1/analytics` — returns a Spring Data `Page<Analytics>` with pagination (`page`, `size` params)
   - `GET /api/v1/analytics/{shortCode}` — returns `List<Analytics>` for a specific short code (all dates)

---

### Shared Infrastructure

#### Redis (In-Memory Data Store)

**What it is:** An ultra-fast key-value store that lives entirely in RAM. Much faster than a database because it doesn't touch the disk. We use **Redis Stack Server** which includes the RedisBloom module for Bloom filters.

**What we use it for (6 different purposes):**

| Purpose | Key Pattern | Value | TTL | Used By |
|---------|-------------|-------|-----|---------|
| **ShortCode Cache** | `shortKey:<code>` | ShortCode JSON | 1 day | Shortener + Redirect |
| **Redirect Cache** | `redirect:<code>` | Original URL string | Until expiry / 7 days | Redirect |
| **URL Hash Cache** | `longUrl:<hash>` | Short code string | 1 day | Shortener |
| **Bloom Filter** | `bloom:shortcodes` | Probabilistic set | Permanent | Redirect |
| **Rate Limiting** | `rate_limit:<route>:<ip>` | Counter (integer) | 1 minute | Gateway |
| **Distributed Locks** | `lock:<key>` | "1" (flag) | 2 seconds | Shortener + Redirect |
| **ID Generator** | `id_generator` | Counter (integer) | Permanent | Shortener |
| **Visitor Tracking** | `visitors:<code>` | Set of hashed IDs | 30 days | Redirect |

**Why Redis over just PostgreSQL:**
- Data is in memory → reads are **microseconds** instead of **milliseconds** (100-1000x faster)
- Removes database load for the most frequent operations (redirects — which get the most traffic)
- Redis has built-in data structures that PostgreSQL doesn't: Bloom filter, atomic counters, Sets with expiration
- Lettuce (Redis client) metrics are enabled for monitoring

#### PostgreSQL (Relational Database)

**What it is:** The source of truth. All data is persisted here on disk. Redis can be cleared without any data loss — Redis is just a cache.

**Two schemas:**

1. **`short_url` schema** — used by Shortener and Redirect services:
   - `short_code` table — the main URL mappings (id, short_code, original_url, alias, password_hash, is_protected, expires_at, max_clicks, unique_visitor_count, current_version_id, timestamps)
   - `url_version` table — version history for each URL (id, short_code_id, version_number, original_url, alias, password, expires_at, rollback_from_version, is_rollback, timestamps)
   - `id_checkpoint` table — singleton row tracking the highest ID batch ever issued; protects against ID counter rewind after Redis restart

2. **`analytics` schema** — used by Analytics Service:
   - `analytics` table — daily rollup of visit analytics (id, short_code, access_date, total_visit_count, browser/device/OS counts as JSON, geo fields, referrer, UTM, bot flag, recent access times)

**Key configurations:**
- HikariCP connection pool: max 50 connections
- Hibernate batch size: 50
- Flyway for schema migrations (versioned SQL files)
- Unique constraint on `analytics(shortCode, accessDate)` for upsert pattern

#### Kafka (Message Queue)

**What it is:** A distributed streaming platform. Think of it as a **durable message buffer** between the Redirect Service (producer) and Analytics Service (consumer). We use **Apache Kafka 4.1.1 in KRaft mode** (no ZooKeeper dependency).

**Why we use Kafka instead of direct REST calls:**

- **Decoupling** — The redirect doesn't wait for analytics to be saved. The user gets redirected instantly (sub-100ms); analytics are processed later (within milliseconds to seconds). Slowing down a redirect for analytics would be a terrible user experience.

- **Buffering (Shock Absorption)** — During traffic spikes, Kafka acts as a shock absorber. Events accumulate in the queue and are processed at a steady pace. The Analytics Service never gets overwhelmed because it controls the consumption rate.

- **Batch Processing** — The consumer processes events in batches of up to 500, which is much more efficient than 500 individual database inserts. Batch inserts reduce database round-trips and lock contention.

- **Reliability** — Kafka stores events on disk with configurable replication. Even if the Analytics Service crashes and restarts, the events are still in Kafka and will be consumed. No data loss.

- **Dead Letter Topic (DLT)** — Events that can't be processed after retries are automatically sent to a dead letter queue for manual inspection and reprocessing.

- **Idempotent Producer** — `enable.idempotence=true` guarantees exactly-once semantics for the producer, preventing duplicate events even in case of network issues.

**Configuration:**
- KRaft mode (no ZooKeeper) — simpler deployment, single process
- `acks=all` — producer waits for all in-sync replicas to acknowledge
- Single-node cluster (suitable for development; production would use 3+ brokers for high availability)
- Data stored on `tmpfs` (in-memory) — suitable for development; production would use persistent volumes

---

## The Two Main Request Flows (Visual)

### Flow 1: Creating a Short URL

```
YOU                        GATEWAY              SHORTENER           REDIS        POSTGRES
 │                          │                     │                    │             │
 │ POST /api/v1/urls/shorten│                     │                    │             │
 │ {"longUrl":"https://..."}│                     │                    │             │
 │─────────────────────────▶│────────────────────▶│                    │             │
 │                          │                     │                    │             │
 │                          │ 1. Rate limit check │                    │             │
 │                          │ 2. Route to         │                    │             │
 │                          │    shortener        │                    │             │
 │                          │                     │ 3. Validate URL    │             │
 │                          │                     │ 4. Normalize URL   │             │
 │                          │                     │ 5. Check alias     │             │
 │                          │                     │    uniqueness      │             │
 │                          │                     │                    │             │
 │                          │                     │ 6. Check cache     │────────────▶│
 │                          │                     │    (longUrl:hash)  │◀────────────│
 │                          │                     │                    │  (miss)     │
 │                          │                     │                    │             │
 │                          │                     │ 7. Acquire lock    │────────────▶│
 │                          │                     │    (SETNX 2s TTL)  │◀────────────│
 │                          │                     │                    │             │
 │                          │                     │ 8. Check DB dup    │             │──────────▶│
 │                          │                     │    (by URL)        │             │◀─────────│
 │                          │                     │                    │             │ (not found)
 │                          │                     │ 9. INCRBY 1000     │────────────▶│
 │                          │                     │    (batch reserve) │◀────────────│
 │                          │                     │                    │             │
 │                          │                     │ 10. Checkpoint to  │             │──────────▶│
 │                          │                     │     id_checkpoint  │             │◀─────────│
 │                          │                     │     (PG)           │             │          │
 │                          │                     │                    │             │
 │                          │                     │ 11. Obfuscate ID   │             │
 │                          │                     │     (* Knuth)      │             │
 │                          │                     │                    │             │
 │                          │                     │ 12. Base62 encode  │             │
 │                          │                     │     (unsigned)     │             │
 │                          │                     │                    │             │
 │                          │                     │ 13. Save URL       │             │──────────▶│
 │                          │                     │ 14. Create v1      │             │──────────▶│
 │                          │                     │ 15. Update current │             │──────────▶│
 │                          │                     │     version ptr    │             │           │
 │                          │                     │                    │             │
 │                          │                     │ 16. Populate cache │────────────▶│
 │                          │                     │ 17. Release lock   │────────────▶│
 │                          │                     │ 18. Populate bloom │────────────▶│
 │                          │                     │                    │             │
 │◀─────────────────────────│◀────────────────────│                    │             │
 │ {shortUrl, qrCodeUrl,    │                     │                    │             │
 │  expiresAt, ...}         │                     │                    │             │
```

### Flow 2: Visiting a Short Link (Redirect)

```
BROWSER                   GATEWAY              REDIRECT              REDIS         POSTGRES      KAFKA
 │                          │                     │                     │              │            │
 │ GET /{shortCode}         │                     │                     │              │            │
 │─────────────────────────▶│────────────────────▶│                     │              │            │
 │                          │                     │                     │              │            │
 │                          │ 1. Rate limit check │                     │              │            │
 │                          │ 2. Route to redirect│                     │              │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 3. Bloom filter     │──────────────▶│            │
 │                          │                     │    (BF.EXISTS)      │◀──────────────│            │
 │                          │                     │                     │   (yes)      │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 4. Cache lookup     │──────────────▶│            │
 │                          │                     │    (shortKey:code)  │◀──────────────│            │
 │                          │                     │                     │   (miss)     │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 5. Cache stampede   │──────────────▶│            │
 │                          │                     │    (SETNX lock)     │◀──────────────│            │
 │                          │                     │                     │              │            │
 │                          │                     │ 6. DB lookup        │              │──────────▶│
 │                          │                     │    (by code/alias)  │              │◀─────────│
 │                          │                     │                     │              │ (found)   │
 │                          │                     │                     │              │            │
 │                          │                     │ 7. Populate cache   │──────────────▶│            │
 │                          │                     │                     │              │            │
 │                          │                     │ 8. Check password?  │              │            │
 │                          │                     │    (show form if    │              │            │
 │                          │                     │     protected)      │              │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 9. Check expiry     │              │            │
 │                          │                     │    & max clicks     │              │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 10. Cache redirect  │──────────────▶│            │
 │                          │                     │     URL in Redis    │              │            │
 │                          │                     │                     │              │            │
 │                          │                     │ 11. Track unique    │──────────────▶│            │
 │                          │                     │     visitor (SADD)  │◀──────────────│            │
 │                          │                     │                     │              │            │
 │                          │                     │ 12. Send analytics  │              │            │──────────▶
 │                          │                     │     to Kafka        │              │            │ (async)
 │                          │                     │                     │              │            │
 │◀─── 302 Redirect ────────│◀────────────────────│                     │              │            │
 │  to original URL          │                     │                     │              │            │
 │                                                                                                │
 │  (Meanwhile, Analytics Service eventually consumes the Kafka event,                             │
 │   enriches it with GeoIP data, and stores it in PostgreSQL)                                     │
```

---

## Architecture Decisions (Why we chose what we did)

| Decision | Choice | Why |
|----------|--------|-----|
| **Microservices** (4 services) | Spring Boot + Docker | Each service can scale independently; one service failing doesn't take down others; teams can work on them separately |
| **API Gateway** | Spring Cloud Gateway | Single entry point for routing, rate limiting, circuit breaking; reactive (non-blocking) for high throughput |
| **ID Generation** | Redis `INCRBY 1000` + Postgres checkpoint + Knuth obfuscation | Batch (1000 IDs/reservation) minimizes Redis round-trips to ~1/sec. Postgres checkpoint survives Redis restart without collision. Knuth obfuscation prevents sequential enumeration. |
| **Short Code Encoding** | Base62 (0-9, a-z, A-Z) | URL-safe, case-sensitive (more combinations than Base58), no special characters |
| **Duplicate Detection** | MD5 hash of normalized URL | Constant-time lookup; normalization ensures same URL always produces same hash; case-insensitive matching |
| **Cache** | Redis (in-memory) | Microsecond reads, built-in TTL, distributed (all instances share same cache) |
| **Bloom Filter** | Redis Stack `BF.*` commands | Fast rejection of non-existent codes before cache/DB; 1% false positive rate acceptable; 10M capacity |
| **Cache Stampede Protection** | Redis `SETNX` mutex (2s TTL) | Only 1 concurrent DB lookup per key; others wait briefly and use cache; simple and effective |
| **Analytics Pipeline** | Kafka producer → batch consumer | Decouples redirect latency from analytics writes; batch DB inserts (up to 500); durable message buffer |
| **Rate Limiting** | Redis sliding window counter | Distributed (not per-instance); per-route configurable limits; atomic increments |
| **Password Hashing** | bcrypt | Industry standard, adaptive cost factor, resistant to brute force |
| **Password Attempts** | HTTP Session (3 attempts / 6 hours) | Session-scoped (not global); prevents brute force per user; no database writes for rate limiting |
| **Encrypted Cookies** | Spring Security `Encryptors.text` | Key rotation support (multiple keys); AES encryption for "Remember Me" tokens |
| **URL Validation** | Syntax check + HEAD request | Prevents creating links to invalid or unreachable URLs; HEAD is lightweight (no body download) |
| **GeoIP** | MaxMind GeoLite2 (City + ASN) | Free databases, industry standard, offline lookups (no external API calls), circuit breaker protected |
| **User-Agent Parsing** | YAUS (Yet Another User Agent Analyzer) | Fast, accurate, caches results (10K entries), extracts device/browser/OS in one pass |
| **Error Responses** | Consistent JSON envelope | All services return `{timestamp, status, error, message, path, traceId}` for predictable client handling |
| **Tracing** | OpenTelemetry → Jaeger | Industry standard for distributed tracing; auto-instrumentation with `@WithSpan`; OTLP exporter |
| **Metrics** | Micrometer → Prometheus | Standard JVM metrics + custom counters/timers; Lettuce Redis metrics enabled |
| **Logging** | JSON structured → Loki | Machine-parseable, searchable via Grafana; no file-based logging; structured fields for filtering |
| **Database Migrations** | Flyway | Versioned SQL files; automatic migration on startup; separate migration for each schema |
| **Containerization** | Docker with resource limits | 512MB memory + 0.5 CPU per service; ensures fair resource sharing; easy deployment |

---

## API Endpoints

### URL Management (via Gateway at `http://localhost:8080`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/urls/shorten` | Create a short URL | None |
| PUT | `/api/v1/urls/{code}` | Update a short URL | None |
| DELETE | `/api/v1/urls/{code}` | Delete a short URL | None |
| GET | `/api/v1/urls/{code}` | Resolve/get info about a short URL | None |

### QR Codes

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/qr/{code}?format=png&size=300&color=FF0000` | Generate QR code |

### URL Versions

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/urls/{code}/versions` | List all versions |
| GET | `/api/v1/urls/{code}/versions/current` | Get current version |
| GET | `/api/v1/urls/{code}/versions/compare?from=1&to=2` | Compare two versions |
| POST | `/api/v1/urls/{code}/versions/rollback-to/{version}` | Rollback to a version |
| DELETE | `/api/v1/urls/{code}/versions/{version}` | Delete a specific version |

### Redirect

| Method | Path | Description |
|--------|------|-------------|
| GET | `/{code}` | Redirect to original URL (returns 302) |
| GET | `/{code}/verify` | Show password form |
| POST | `/{code}/verify` | Submit password for protected URL |

### Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/analytics?page=0&size=20` | Get all analytics (paginated) |
| GET | `/api/v1/analytics/{code}` | Get analytics for a specific short code |

### Health & Monitoring

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/actuator/health` | Overall health |
| GET | `/api/v1/actuator/health/liveness` | Liveness probe |
| GET | `/api/v1/actuator/health/readiness` | Readiness probe |
| GET | `/api/v1/actuator/prometheus` | Prometheus metrics |

---

## How to Run

### Prerequisites
- JDK 26+
- Docker Desktop
- Gradle 9.5+ (wrappers included)

### Quick Start (everything in Docker)

```bash
cd docker
docker compose \
  -f docker-compose.infrastructure.yml \
  -f docker-compose.dev.yml \
  -f docker-compose.monitoring.yml \
  up -d --build
```

This starts **12 containers**: postgres, redis, kafka, shortener-service, redirect-service, analytics-service, api-gateway, prometheus, grafana, jaeger, loki, alloy.

### Access Everything

| Tool | URL |
|------|-----|
| **API Gateway** | http://localhost:8080 |
| **Grafana** | http://localhost:3000 (admin/admin) |
| **Jaeger** | http://localhost:16686 |
| **Prometheus** | http://localhost:9090 |

### End-to-End Test

```bash
# Create a short URL
curl -s -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com"}'

# Follow the redirect
SHORT_CODE=$(curl -s -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com"}' | jq -r '.shortUrl' | grep -oE '[^/]+$')
curl -s -o /dev/null -w "%{redirect_url}" http://localhost:8080/$SHORT_CODE

# Query analytics
curl -s http://localhost:8080/api/v1/analytics/$SHORT_CODE
```

---

## Production Considerations

This project uses PostgreSQL and batch Redis INCR for development convenience. At production scale, two changes are strongly recommended:

### Replace PostgreSQL with DynamoDB / Cassandra for URL mappings

The redirect path is a **pure key-value lookup**: `short_code → original_url`. PostgreSQL is a relational database with full ACID, joins, and schema enforcement — most of which goes unused for the 100K+ reads/sec that the redirect service handles. In production:

- **DynamoDB** (AWS) or **Cassandra** (self-hosted) give single-digit-millisecond reads at any scale, with automatic partitioning and no single-writer bottleneck
- Keep **PostgreSQL for the management plane** (URL CRUD, version history, analytics queries) where joins and complex queries matter
- This is a **CQRS pattern**: one DB optimized for writes (Postgres for management) and one for reads (DynamoDB/Cassandra for redirects)
- The URL mappings are small documents (~1KB) — perfect for DynamoDB's item size limits

### Replace batch Redis INCR with Snowflake ID generation

The current batch `INCRBY 1000` + Postgres checkpoint approach works well, but at production scale:

- **Snowflake ID** (Twitter's algorithm) generates IDs **locally in-memory** — zero network calls, zero Redis dependency for the ID path
- Each instance generates IDs independently using `timestamp + worker ID + sequence`
- No Postgres checkpoint writes needed (~1 write/sec becomes 0 writes/sec)
- 64-bit IDs fit in the same Base62 encoding with no changes to the short code format
- Requires a **worker ID registry** (ZooKeeper/etcd or a small DB table) so Docker containers don't collide on MAC-derived worker IDs

| Factor | Batch Redis INCR (current) | Snowflake (production) |
|--------|---------------------------|------------------------|
| Network calls | 1 per 1000 IDs | 0 per ID |
| Durability | Redis + Postgres checkpoint | None needed (timestamp-based) |
| Collision risk | Zero (atomic INCR) | Zero (with proper worker ID mgmt) |
| Complexity | Redis + Postgres dependency | Worker ID registry needed |
| Best for | MVP / moderate scale | 100M+ DAU / global deployment |

## Project Structure

```
backend/
├── api-gateway/           # Spring Cloud Gateway (routes, rate limiting, CORS, circuit breakers)
├── shortener-service/     # URL CRUD, QR generation, versioning, cache stampede protection
├── redirect-service/      # Redirect handler, password verify, analytics producer, Bloom filter
├── analytics-service/     # Kafka consumer, GeoIP enrichment, analytics query API
└── docker/
    ├── docker-compose.infrastructure.yml   # postgres, redis, kafka
    ├── docker-compose.dev.yml              # 4 microservices (with resource limits: 512MB, 0.5 CPU each)
    ├── docker-compose.monitoring.yml       # prometheus, grafana, jaeger, loki, alloy
    ├── .env                                # shared environment variables
    ├── prometheus/prometheus.yml
    ├── grafana/provisioning/               # auto-provisioned datasources + dashboards
    ├── loki/loki.yml                       # Loki config (TSDB, tmpfs storage)
    └── alloy/alloy.river                   # Alloy config (Docker log scraping)
```
