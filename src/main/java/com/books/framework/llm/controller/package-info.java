/**
 * HTTP management and execution APIs for the LLM module.
 *
 * <p><strong>Security:</strong> Controllers under {@code /api/llm/**} are registered without
 * authentication. The host Spring Boot application must configure Spring Security (or an API
 * gateway) to protect these routes before exposing them on untrusted networks. See
 * {@code SECURITY.md} and {@code README.md} in the repository root.
 */
package com.books.framework.llm.controller;
