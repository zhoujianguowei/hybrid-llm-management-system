package com.grw.xiaobai.hybrid.llm.filter;

import com.grw.xiaobai.hybrid.llm.constant.TraceConstants;
import com.grw.xiaobai.hybrid.llm.utils.ThreadLocalContext;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TimingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        long startTime = System.currentTimeMillis();
        try {
            ThreadLocalContext.put(TraceConstants.HTTP_REQUEST_KEY, request);
            log.debug("start request uri={}", uri);
            chain.doFilter(request, response);
        } finally {
            ThreadLocalContext.remove(TraceConstants.HTTP_REQUEST_KEY);
            log.debug("end request uri={} cost={} ms", uri, System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
