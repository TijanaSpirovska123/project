class LLMUnavailableError(RuntimeError):
    """The configured LLM provider could not complete the request."""


class LLMInvalidResponseError(RuntimeError):
    """The LLM returned an empty or invalid structured response."""