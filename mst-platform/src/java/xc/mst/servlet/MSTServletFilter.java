/**
 * Copyright (c) 2010 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */
package xc.mst.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public class MSTServletFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(MSTServletFilter.class);

    public void init(FilterConfig config) throws ServletException {
    }

    public void destroy() {
    }

    public void doFilter(ServletRequest req, ServletResponse resp,
            FilterChain chain) throws IOException, ServletException {
        // BDA I originally wrote this for the purpose of the below commented out code. I ended up switching
        // to using Spring's HibernateTemplate method, which means that we don't NEED to open and close
        // connections explicitly. We could do this, though, as it might be nice to have a transaction per
        // request. However, I'm not going to bother with it just yet. I left this filter in the web.xml
        // so we can just add code when we need it.
        /*
        SessionFactory sessionFactory = (SessionFactory)MSTConfiguration.getInstance().getBean("SessionFactory");
        sessionFactory.openSession();
        */
        HttpServletRequest hsr=null;
        try {
            hsr = (HttpServletRequest) req;
            LOG.debug("hsr.getRequestURI(): " + hsr.getRequestURI());
            if (hsr.getRequestURI().contains("/solr/") || hsr.getRequestURI().contains("/devAdmin")) {
                if (hsr.getSession().getAttribute("user") == null) {
                    HttpServletResponse hresp = (HttpServletResponse) resp;
                    String uri = hsr.getRequestURI();
                    int idx0 = uri.indexOf("/solr/");
                    if (idx0 == -1) {
                        idx0 = uri.indexOf("/devAdmin");
                    }
                    uri = uri.substring(0, idx0) + "/st/";

                    hresp.sendRedirect(uri);

                    return;
                }
            }
            if (hsr.getRequestURI().startsWith("/pub") && !hsr.getRequestURI().endsWith("oaiRepository")) {
                HttpServletResponse hresp = (HttpServletResponse) resp;
                String uri = hsr.getRequestURI();
                uri = uri.replaceAll("pub", "st");
                hresp.sendRedirect(uri);

                return;
            }
            req.setCharacterEncoding("UTF-8");
            chain.doFilter(req, resp);
        } catch (Throwable t) {
            LOG.error("Exception, initial URI:  hsr.getRequestURI(): " + hsr.getRequestURI(), t);
        } finally {
            // sessionFactory.close();
        }
    }

}
