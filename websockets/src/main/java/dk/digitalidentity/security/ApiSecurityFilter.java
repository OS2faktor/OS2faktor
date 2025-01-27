package dk.digitalidentity.security;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiSecurityFilter implements Filter {
	private String apiKey;

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// we are using a custom header instead of Authorization because the Authorization header plays very badly with the SAML filter
		String authHeader = request.getHeader("ApiKey");
		if (authHeader != null) {
			boolean apiKeyMatch = false;
			
			if (authHeader.equals(apiKey)) {
				apiKeyMatch = true;
			}
			
			if (!apiKeyMatch) {
				unauthorized(response, "Invalid ApiKey header", authHeader);
				return;
			}

			filterChain.doFilter(servletRequest, servletResponse);
		}
		else {
			unauthorized(response, "Missing ApiKey header", authHeader);
		}
	}

	private static void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
		log.warn(message + " (authHeader = " + authHeader + ")");
		response.sendError(401, message);
	}

	@Override
	public void destroy() {
		;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		;
	}
}
