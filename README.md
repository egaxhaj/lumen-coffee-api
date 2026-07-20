# Lumen Coffee Roasters — an agent-first API

> Browsers will go away. Websites will go away. Software will go away.
> The only thing we directly use will be the AI agent.
> — [Mark Kretschmann](https://x.com/mark_k/status/2077800677191888917)

This is a working demo of that thesis: a fictional coffee roastery — **Lumen Coffee
Roasters** — whose entire web presence is a single HTTP API. There is no separate
website. An agent given nothing but one root URL can discover what the business is,
read documentation written specifically for it, retrieve the brand two different ways,
and complete a real task (ordering coffee) purely by following hypermedia links — no
out-of-band knowledge, no scraping, no separate docs site.

Purely fictional brand, built for this demo.

## The four things an agent can do here

1. **Discover capabilities** — every response carries HAL `_links`. Starting from
   `GET /api`, an agent can reach everything else without ever constructing a URL by
   hand.
2. **Read agent-targeted documentation** — the OpenAPI document at `/v3/api-docs` is
   generated dynamically by [springdoc](https://springdoc.org/) from the controllers on
   every request (add an endpoint, it appears immediately — nothing is hand-maintained).
   Every operation carries an `x-llm` extension (`whenToUse`, `preconditions`,
   `followUpRels`, `examplePrompt`) written specifically for an LLM reading the spec.
3. **Get the brand two ways** — as an `x-branding` extension embedded at the root of the
   OpenAPI document (palette, typography, voice, logo, all inline — zero extra fetches),
   and as a standalone `GET /brand` resource, content-negotiated into `text/html`
   (a full styled page), `text/markdown`, or `application/json` (structured tokens).
4. **Act** — `GET /api/products/{id}` returns a HAL-FORMS affordance (`_templates`) that
   describes exactly how to place an order; the agent POSTs to `/api/orders` using only
   what that affordance told it, no documentation reading required.

## Architecture

```
src/main/java/com/lumen/api/
  LumenApiApplication.java         # @EnableHypermediaSupport(HAL, HAL_FORMS)
  config/
    OpenApiConfig.java             # agent-facing info.description + x-branding customizer
    CorsConfig.java                # fully open CORS (demo, no auth)
  model/
    Product.java, Order.java, OrderStatus.java     # domain
    ProductRepository.java, OrderRepository.java   # in-memory stores, no DB
    ProductModel.java, OrderModel.java, RootModel.java, SearchHit.java  # HAL view models
    OrderRequest.java              # POST /api/orders body (plain bean — see note below)
  controller/
    RootController.java            # GET /api (HAL entry point) + GET / (human page)
    ProductController.java         # GET /api/products, GET /api/products/{id}
    OrderController.java           # GET/POST /api/orders, GET/DELETE /api/orders/{id}
    BrandController.java           # GET /brand, content-negotiated
    SearchController.java          # GET /api/search?q=
  assembler/
    ProductAssembler.java          # links + placeOrder HAL-FORMS affordance
    OrderAssembler.java            # links + cancel HAL-FORMS affordance
  support/
    BrandTokens.java                # single source of truth for brand tokens
    Presentation.java, PresentationService.java  # branded HTML/markdown fragments
    BrandPreferenceResolver.java   # Prefer: brand=inline|none negotiation
src/main/resources/
  application.yml
  brand/brand.html, brand/brand.md # the two static brand representations
PROMPTS.md                         # demo scripts for Claude Code / Claude Desktop
```

### Key request flow: discover → order

```
GET /api
  └─ x:products → GET /api/products
       └─ self   → GET /api/products/{id}          (carries _embedded.presentation
                                                       + a HAL-FORMS affordance)
            └─ _templates.default → POST /api/orders  {productId, quantity}
                 └─ self / x:status → GET /api/orders/{id}
                      └─ _templates.default → DELETE /api/orders/{id}   (cancel, while PLACED)
```

### Notable implementation details

- **HAL-FORMS naming**: Spring HATEOAS keys the *first* (and, here, only) affordance on
  a resource's links as `"default"` in `_templates` — not by the name passed to
  `.withName(...)` — that name is only used once a second affordance exists on the same
  model. All agent-facing docs (`x-llm.followUpRels`, `info.description`) reference
  `_templates.default` accordingly, matching what actually comes back on the wire.
- **`OrderRequest` is a plain JavaBean, not a record.** Spring HATEOAS's HAL-FORMS
  property builder uses classic getter/setter introspection; record accessors
  (`productId()`) aren't recognized as writable, which marked the generated form fields
  `readOnly: true`. A conventional bean keeps them genuinely fillable.
- **`Prefer: brand=inline|none`** (RFC 7240) on `GET /api/products/{id}`,
  `POST /api/orders`, and `GET /api/orders/{id}`: no header (or `brand=inline`) returns
  a branded `_embedded.presentation` fragment (HTML + markdown) plus a
  `Brand-Version: v1` response header; `brand=none` omits it and confirms with
  `Preference-Applied: brand=none`. An agent that already fetched `/brand` is expected
  to send `brand=none` from then on.
- **Capability search** (`GET /api/search?q=`) calls the app's own `/v3/api-docs` over
  HTTP at request time and scans summaries/descriptions/`x-llm` text — the OpenAPI
  document *is* the search index, and it's always current since springdoc regenerates it
  from the controllers.

## Running it

```bash
./mvnw spring-boot:run
```

The server listens on `:8080`. Try the whole flow with curl, following only returned
links:

```bash
curl -s http://localhost:8080/api | jq
curl -s http://localhost:8080/v3/api-docs | jq '."x-branding", .paths["/api/products/{id}"].get."x-llm"'
curl -s http://localhost:8080/api/products | jq
curl -s -H 'Accept: application/prs.hal-forms+json' http://localhost:8080/api/products/1 | jq
curl -s -X POST http://localhost:8080/api/orders -H 'Content-Type: application/json' \
     -d '{"productId": 1, "quantity": 2}' | jq
curl -s -H 'Accept: text/html' http://localhost:8080/brand
```

Swagger UI (for a human reviewing the demo) is at `http://localhost:8080/swagger-ui.html`.

### Testing with an agent

- **Claude Code (local)**: just point it at `http://localhost:8080/api` — it has a real
  HTTP client (curl), so pure OpenAPI + HATEOAS is sufficient. See `PROMPTS.md`.
- **Claude Desktop**: two gaps to bridge, discovered the hard way:
  1. *Reachability*: Desktop's built-in fetch runs through Anthropic's servers (no
     localhost) and is GET-only with no custom headers — it can read the API but can
     never POST an order. Its code-execution sandbox blocks arbitrary egress entirely.
  2. *Capability*: the fix is the generic MCP server in `mcp/http_mcp_server.py` — a
     single API-agnostic `http_request` tool (~80 lines, stdlib only, retries on 503).
     It grants Desktop a real HTTP client; the API remains the only source of truth
     about what to call. Add to `claude_desktop_config.json` and restart Desktop:
     ```json
     {
       "mcpServers": {
         "http": {
           "command": "python3",
           "args": ["/absolute/path/to/mcp/http_mcp_server.py"]
         }
       }
     }
     ```

### Deploying (Render)

The repo carries a `render.yaml` blueprint (free plan, Docker runtime, health check on
`/api`) and a multi-stage `Dockerfile`. Connect the repo as a Blueprint in the Render
dashboard; pushes to `main` auto-deploy. Two behaviors worth knowing:

- The app honors Render's `PORT` env var and `X-Forwarded-*` headers
  (`server.forward-headers-strategy: framework`), so all HAL links and the OpenAPI
  `servers` entry come out as the public https URL automatically.
- Free instances sleep after 15 idle minutes and take ~30–60s to wake (first request
  gets a 503 with `Retry-After`). Pre-warm before demos, or point a free uptime pinger
  at `/api`. The MCP server's 503 retry absorbs this automatically.
- HAL resources are served as plain `application/json` by default (same body): some
  agent fetch pipelines classify the nonstandard `application/hal+json` media type as
  binary. Clients that explicitly ask for `application/hal+json` or
  `application/prs.hal-forms+json` still get those.

## Notable deviations from the original design note

- **Spring Boot version**: the plan called for Spring Boot 3.3 with the Maven wrapper.
  `start.spring.io` on this machine's clock (July 2026) only generates Spring Boot
  4.x projects by default (its compatibility range no longer covers 3.x). Since
  springdoc-openapi's stable 2.x line targets Spring Framework 6 / Boot 3, the
  generated `pom.xml`'s parent version was pinned back to **Spring Boot 3.3.5** by
  hand (with the accompanying 3.x artifact names, e.g. `spring-boot-starter-web`
  instead of Boot 4's `spring-boot-starter-webmvc`) to keep the stack the plan
  actually specified.
- Everything else in the Design section (HAL root, dynamic OpenAPI + `x-llm` +
  `x-branding`, `/brand` content negotiation, HAL-FORMS affordances, `Prefer` header
  negotiation with presentation fragments, capability search, minimal human page, open
  CORS) is implemented as specified.
