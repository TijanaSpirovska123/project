# AI Marketing Insights Assistant

## Goal

This project is a Phase 0 LLM integration prototype built with Python, FastAPI, and Pydantic.

The goal is to demonstrate the basic backend foundations needed for LLM-based systems:

- LLM API integration
- prompt design
- structured output
- schema validation
- error handling
- example-driven testing
- basic understanding of safe AI output handling

## Scope

This is a standalone learning prototype using mock marketing-related data.

It is not connected to any production application.
It does not use real customer data.
It does not reference any internal marketing application.

## Technologies

- Python
- FastAPI
- Pydantic
- Google Gemini API
- python-dotenv
- pytest
- Swagger UI / Postman

## Application Flow

```text
Client request
    ↓
FastAPI endpoint
    ↓
Pydantic request validation
    ↓
LLM service
    ↓
Gemini API or mock mode
    ↓
Pydantic response validation
    ↓
Structured JSON response