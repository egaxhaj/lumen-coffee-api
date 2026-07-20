#!/usr/bin/env python3
"""A complete, dependency-free MCP server: one generic http_request tool.

MCP over stdio is newline-delimited JSON-RPC 2.0. This file is the entire
"protocol": three methods (initialize, tools/list, tools/call). It knows
nothing about any particular API — the agent learns endpoints from the
API's own OpenAPI document and HATEOAS links.
"""
import json
import sys
import urllib.request
import urllib.error

TOOL = {
    "name": "http_request",
    "description": (
        "Generic HTTP client. Make any HTTP request and get the raw response "
        "back verbatim. Use APIs' own discovery mechanisms (root links, "
        "OpenAPI documents) to learn what to call — this tool has no "
        "API-specific knowledge. Retries once on 503 (sleeping free-tier "
        "hosts wake in ~60s)."
    ),
    "inputSchema": {
        "type": "object",
        "properties": {
            "method": {"type": "string", "enum": ["GET", "POST", "PUT", "PATCH", "DELETE"]},
            "url": {"type": "string"},
            "headers": {"type": "object", "additionalProperties": {"type": "string"}},
            "body": {"type": "string", "description": "Request body (e.g. JSON string)"},
        },
        "required": ["method", "url"],
    },
}


def do_request(args):
    req = urllib.request.Request(
        args["url"],
        method=args["method"],
        data=args.get("body", "").encode() if args.get("body") else None,
        headers=args.get("headers") or {},
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return resp.status, dict(resp.headers), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")


def handle(msg):
    method, msg_id, params = msg.get("method"), msg.get("id"), msg.get("params", {})
    if method == "initialize":
        result = {
            "protocolVersion": "2025-06-18",
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "generic-http", "version": "1.0.0"},
        }
    elif method == "tools/list":
        result = {"tools": [TOOL]}
    elif method == "tools/call" and params.get("name") == "http_request":
        args = params.get("arguments", {})
        status, headers, body = do_request(args)
        if status == 503:  # sleeping free-tier host — wait for wake-up and retry
            import time
            time.sleep(int(headers.get("Retry-After", 30)) + 30)
            status, headers, body = do_request(args)
        interesting = {k: v for k, v in headers.items() if k.lower() in
                       ("content-type", "location", "brand-version", "preference-applied", "etag")}
        text = f"HTTP {status}\n" + "".join(f"{k}: {v}\n" for k, v in interesting.items()) + "\n" + body
        result = {"content": [{"type": "text", "text": text}], "isError": status >= 400}
    elif msg_id is None:
        return None  # notification (e.g. notifications/initialized): no response
    else:
        return {"jsonrpc": "2.0", "id": msg_id,
                "error": {"code": -32601, "message": f"Method not found: {method}"}}
    return {"jsonrpc": "2.0", "id": msg_id, "result": result}


if __name__ == "__main__":
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        response = handle(json.loads(line))
        if response is not None:
            print(json.dumps(response), flush=True)
