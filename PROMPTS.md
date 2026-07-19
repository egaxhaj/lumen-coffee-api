# Demo prompts

Point an agent at the root URL and nothing else. Locally with Claude Code, that's
`http://localhost:8080/api`. For Claude Desktop, tunnel first (see README.md) and use
the public URL instead.

## 1. Full discover → order → brand flow

> Starting only from http://localhost:8080/api and nothing else, find out what this
> business is, how to order a coffee, place an order for one, and show me the order
> confirmation styled in their branding.

Expect the agent to:
- `GET /api`, follow `describedby` to `/v3/api-docs` and/or `x:brand` to `/brand` to
  learn about the business and its branding,
- follow `x:products` to `GET /api/products`, then a product's `self` link,
- read the HAL-FORMS affordance in `_templates` on that product response and `POST` to
  `/api/orders` with the properties it describes,
- render the `_embedded.presentation` fragment from the response (or, if it already
  fetched `/brand`, send `Prefer: brand=none` on subsequent requests and style the
  confirmation itself using the brand tokens).

## 2. Branding only

> Show me this business's branding.

Expect a `GET /brand` (or reading `x-branding` from the OpenAPI document) and a
rendering that uses the amber/charcoal/cream palette and the stated voice — not generic
styling.

## 3. Capability search

> Search this API for how to cancel an order.

Expect `GET /api/search?q=cancel`, a top match on `DELETE /api/orders/{id}`
("Cancel an order"), and the agent explaining that a specific order id (and its status
being `PLACED`) is required — ideally noting the affordance appears as `_templates`
on the order resource itself while it's still cancellable.

## 4. Dynamic docs sanity check (for you, not the agent)

> Add a throwaway endpoint, hit /v3/api-docs, confirm it shows up, remove it.

This isn't a prompt for the agent — it's how to verify springdoc's dynamic-doc claim
yourself: add a `@GetMapping`, restart, `curl localhost:8080/v3/api-docs | jq '.paths'`
and see the new path appear with zero manual spec edits.

## 5. Claude Desktop (remote)

Same as prompt 1, but give Claude Desktop the tunneled public URL's `/api` path instead
of localhost, and additionally ask:

> Render the order confirmation as a styled artifact using their branding.

This exercises the harder path: Claude Desktop's fetch tool runs through Anthropic's
servers, so it can only reach a publicly reachable URL, not `localhost`.
