# Findings: what it took to make a business agent-first

The experiment: prove the thesis of [this X post](https://x.com/mark_k/status/2077800677191888917) —

> Browsers will go away. Websites will go away. Software will go away.
> The only thing we directly use will be the AI agent.

— by building a business with **no website at all** (Lumen Coffee Roasters, fictional)
and testing whether real agents, given nothing but one root URL, could discover it,
learn it, buy from it, and render its brand. Two agents were tested: Claude Code
(terminal, has a real HTTP client) and Claude Desktop (chat app, sandboxed tools).

**Result: the thesis holds — with one asterisk about agent capability that is itself
the most interesting finding.**

## What was built

A Spring Boot API that is the business's entire presence:

- **HATEOAS everywhere** (HAL + HAL-FORMS): every response links to what's reachable
  next; write actions appear as affordances (`_templates`) on the resources
  themselves, only while they're valid (pay/cancel exist only on unpaid orders).
- **OpenAPI written for LLMs**: springdoc regenerates `/v3/api-docs` from the
  controllers on every request; every operation carries an `x-llm` extension
  (`whenToUse`, `preconditions`, `followUpRels`, `examplePrompt`).
- **Branding two ways**: an `x-branding` extension in the OpenAPI doc (palette,
  typography, voice, inline SVG logo) and a linked `/brand` page content-negotiated
  into HTML/markdown/JSON — plus per-response branded `_embedded.presentation` cards,
  suppressible with `Prefer: brand=none` once an agent holds the brand tokens.
- **A real transaction**: order → `AWAITING_PAYMENT` → pay affordance → mock payment
  → `PAID` + receipt number + brand-styled receipt card.
- **Capability search**: `GET /api/search?q=` scans the live OpenAPI doc — the spec
  *is* the search index.

## Finding 1 — Self-description is sufficient for a capable agent

Claude Code, given only the root URL cold (fresh session, forbidden from reading the
source), completed everything: discovered the business, read the agent docs, learned
the order affordance from a product response, placed and cancelled orders, adopted
`Prefer: brand=none` after fetching `/brand` *because the x-llm docs told it to*, and
reproduced the branding faithfully. Zero connector code, zero out-of-band knowledge,
zero hand-constructed URLs. Fielding's constraint — hypermedia as the engine of
application state — turns out to be exactly what an LLM agent needs: every response
is a fresh prompt describing what's possible next.

## Finding 2 — When an agent fails, it's capability, not knowledge

Claude Desktop knew *exactly* how to order (it had read the OpenAPI doc) and could
not do it. Its built-in tools are deliberately narrow:

| Desktop tool | Limitation hit |
|---|---|
| Web fetch | GET-only, no custom headers, responses summarized by a small model (paraphrased hrefs), ~15-min cache, refused nonstandard media types as "binary data" |
| Code-execution sandbox | Egress allowlist blocked every host we tried (trycloudflare.com, onrender.com) |

Two server-side accommodations came out of this, both generalizable:
serve HAL bodies as plain `application/json` by default (fetch pipelines don't
recognize `application/hal+json` as text), and tolerate cold-start 503s (free-tier
hosts sleep; agents give up faster than humans).

## Finding 3 — MCP's real contribution is consented capability, not description

The fix for Desktop was an ~80-line, dependency-free MCP server exposing one generic
`http_request` tool ([mcp/http_mcp_server.py](mcp/http_mcp_server.py)). Nothing in it
knows Lumen exists. With it, Desktop completed the full flow — discovery, purchase,
payment, brand-faithful receipt artifact — from the root URL alone.

That factoring is the finding: everything *descriptive* MCP could have carried (which
endpoints exist, how to call them, when to use them) was already in the OpenAPI doc,
and better — inline with the code that serves it, regenerated on every request. What
MCP actually contributed was **pre-authorized transport**: the user consents once
(config entry + first-use prompt), and the agent gains a real HTTP client. MCP is
OAuth-shaped — a capability grant with the agent in the seat third-party apps used to
occupy — not a description format.

Corollary: an MCP server's thickness measures how agent-hostile the underlying
service is. GitHub's hosted MCP is a large curation layer because GitHub's API was
written for programmers. Lumen's is 80 generic lines because the API did the
translation work itself. Hosted `/mcp/` endpoints sitting in front of existing APIs
are this transition's `m.`-sites: real, useful, and temporary — the pressure is
toward self-describing APIs plus ever-thinner generic capability, until hosts ship
the HTTP capability built in and the adapter disappears entirely.

## Finding 4 — Branding survives the death of the website

The receipt artifact Claude Desktop rendered — amber/charcoal/cream, Georgia
headings, the roaster's voice — was styled entirely from data the API published about
itself. A business with no web page got brand-faithful visual presence *inside an
agent conversation*, twice over: pre-rendered cards for agents that want them
(`_embedded.presentation`), raw brand tokens for agents that style themselves, and
the `Prefer` header + `Brand-Version`/ETag machinery to avoid resending either. In
the agent-first world, brand identity becomes another negotiated representation — and
the marketing page becomes an API resource like everything else.

## The division of labor the experiment settles on

| Role | Who | Mechanism |
|---|---|---|
| Describe & teach | The business's API | OpenAPI + `x-llm`, HATEOAS links & affordances |
| Grant capability | The user, once | MCP config entry, consent prompts (OAuth-shaped) |
| Decide & act | The agent | Reads what APIs say, exercises granted capability |

Businesses ship no connectors. Users consent, not configure. Agents do the rest.
That is the world the X post predicts, running today — reproducible from this repo
with one deployed API, one config stanza, and one prompt.

## Reproducing

1. Deploy (or `./mvnw spring-boot:run` + a tunnel) — see README "Deploying".
2. Claude Code: PROMPTS.md §1 against the root URL. No MCP needed.
3. Claude Desktop: register `mcp/http_mcp_server.py` (README "The MCP server"), then
   PROMPTS.md §5. Expect discovery → order → payment → styled receipt artifact.
