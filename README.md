# Crash2Test

Crash2Test is a local AI-powered IntelliJ IDEA plugin that turns a pasted stack trace into a focused debugging summary and practical regression test ideas.

It is built for the moment right after a crash, when you want something more useful than raw logs but less noisy than a generic AI chat.

## Why It Helps

Instead of manually scanning a long exception and guessing where to start, Crash2Test helps you quickly answer:

- What most likely failed
- Which files are worth opening first
- What kind of regression test should be added afterward

Because it uses Ollama locally, your crash analysis stays on your machine.

## How To Use

1. Open the `Crash2Test` tool window in IntelliJ IDEA.
2. Paste a Java or Kotlin stack trace or runtime error.
3. Click `Analyze`.
4. Review the generated summary, likely root cause, files to inspect, and regression test suggestion.
5. Open the resolved source frames directly from the result panel when available.

## What You Get

- A short structured crash summary
- A likely root cause explanation
- Relevant files and stack frames to inspect
- A suggested regression test scenario or code snippet

## Local Setup

Crash2Test expects a local Ollama instance.

- Default URL: `http://localhost:11434`
- Recommended model: `mistral`

If Ollama is not running, the plugin still parses the stack trace and shows a useful fallback view.
