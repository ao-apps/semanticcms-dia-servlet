/*
 * ao-web-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-dia-servlet.
 *
 * ao-web-dia-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-dia-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-dia-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.dia.servlet;

import com.aoindustries.web.dia.servlet.impl.DiaImpl;
import com.semanticcms.core.servlet.PageContext;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Dia {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final String path;

	private String book;
	private int width;
	private int height;

	public Dia(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String path
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.path = path;
	}

	public Dia(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String book,
		String path
	) {
		this(servletContext, request, response, path);
		this.book = book;
	}

	/**
	 * Creates a new dia in the current page context.
	 *
	 * @see  PageContext
	 */
	public Dia(String path) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			path
		);
	}

	/**
	 * Creates a new dia in the current page context.
	 *
	 * @see  PageContext
	 */
	public Dia(String book, String path) {
		this(path);
		this.book = book;
	}

	public Dia book(String book) {
		this.book = book;
		return this;
	}

	public Dia width(int width) {
		this.width = width;
		return this;
	}

	public Dia height(int height) {
		this.height = height;
		return this;
	}

	public void invoke() throws ServletException, IOException {
		DiaImpl.writeDiaImpl(
			servletContext,
			request,
			response,
			response.getWriter(),
			book,
			path,
			width,
			height
		);
	}
}
