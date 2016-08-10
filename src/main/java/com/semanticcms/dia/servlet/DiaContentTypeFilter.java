/*
 * semanticcms-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-dia-servlet.
 *
 * semanticcms-dia-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-dia-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-dia-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.dia.servlet;

import com.semanticcms.dia.model.Dia;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

@WebFilter(
	urlPatterns = "*." + Dia.EXTENSION,
	description = "Filter to add the missing content type for *." + Dia.EXTENSION + " files."
)
public class DiaContentTypeFilter implements Filter {

	/**
	 * MIME type assigned to .dia files.
	 * 
	 * Consistent with <a href="https://www.mediawiki.org/wiki/Extension:Dia">Dia Extension for MediaWiki</a>.
	 */
	private static final String DIA_MIME_TYPE = "application/x-dia-diagram";

	@Override
	public void init(FilterConfig config) {
		// Do nothing
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		resp.setContentType(DIA_MIME_TYPE);
		chain.doFilter(req, resp);
	}

	@Override
	public void destroy() {
		// Do nothing
	}
}
