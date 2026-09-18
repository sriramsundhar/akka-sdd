# Quickstart: Multilingual Greeter Agent

## Prerequisites

- An Anthropic API key (default configured model provider — see `research.md` for why, and how to switch providers).

  ```bash
  export ANTHROPIC_API_KEY="sk-ant-..."
  ```

## Run locally

```bash
mvn compile
akka local start          # starts the local Akka runtime/console (or use akka_local_start via MCP)
mvn exec:java              # or: akka local run-service, once the service is scaffolded
```

## Try it

```bash
curl -X POST localhost:9000/greetings/demo-session-1 \
  -H "Content-Type: application/json" \
  -d '{"message": "hi"}'
# => {"language": "French", "greeting": "Bonjour !"}

curl -X POST localhost:9000/greetings/demo-session-1 \
  -H "Content-Type: application/json" \
  -d '{"message": "hi again"}'
# => {"language": "Japanese", "greeting": "こんにちは！"} (never "French" again until the pool is exhausted)
```

A different `sessionId` starts its own independent rotation:

```bash
curl -X POST localhost:9000/greetings/demo-session-2 \
  -H "Content-Type: application/json" \
  -d '{"message": "hello"}'
# => language selection here is unaffected by demo-session-1's history
```

## Running tests

```bash
mvn test
```

Agent behavior is tested via the SDK's `TestModelProvider`, which substitutes a scripted model response — no real API key or network call is needed for the test suite.
