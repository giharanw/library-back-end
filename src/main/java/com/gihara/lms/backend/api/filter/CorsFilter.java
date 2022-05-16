package com.gihara.lms.backend.api.filter;

import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "CorsFilter", urlPatterns = "/*")
public class CorsFilter extends HttpFilter {
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String origin = req.getHeader("Origin");
        if (origin != null && origin.toLowerCase().contains(getServletContext().getInitParameter("origin"))) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Count");
            res.setHeader("Access-Control-Expose-Headers", "Content-Type, X-Count");
            if (req.getMethod().equals("OPTIONS")) {
                res.setHeader("Access-Control-Allow-Methods", "OPTIONS, GET, PUT, POST, DELETE, HEAD");
            }
        }
        chain.doFilter(req,res);
    }
}
