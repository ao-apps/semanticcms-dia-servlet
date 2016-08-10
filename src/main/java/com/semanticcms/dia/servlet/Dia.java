/*
 * semanticcms-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.io.buffer.SegmentedWriter;
import com.semanticcms.core.model.ElementContext;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.Element;
import com.semanticcms.core.servlet.PageContext;
import com.semanticcms.dia.servlet.impl.DiaImpl;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Dia extends Element<com.semanticcms.dia.model.Dia> {

	public Dia(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String path
	) {
		super(
			servletContext,
			request,
			response,
			new com.semanticcms.dia.model.Dia()
		);
		element.setPath(path);
	}

	public Dia(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String book,
		String path
	) {
		this(servletContext, request, response, path);
		element.setBook(book);
	}

	/**
	 * Creates a new diagram in the current page context.
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
	 * Creates a new diagram in the current page context.
	 *
	 * @see  PageContext
	 */
	public Dia(String book, String path) {
		this(path);
		element.setBook(book);
	}

	@Override
	public Dia id(String id) {
		super.id(id);
		return this;
	}

	public Dia label(String label) {
		element.setLabel(label);
		return this;
	}

	public Dia book(String book) {
		element.setBook(book);
		return this;
	}

	public Dia width(int width) {
		element.setWidth(width);
		return this;
	}

	public Dia height(int height) {
		element.setHeight(height);
		return this;
	}

	private BufferResult writeMe;
	@Override
	protected void doBody(CaptureLevel captureLevel, Body<? super com.semanticcms.dia.model.Dia> body) throws ServletException, IOException, SkipPageException {
		super.doBody(captureLevel, body);
		BufferWriter out = (captureLevel == CaptureLevel.BODY) ? new SegmentedWriter() : null;
		try {
			DiaImpl.writeDiaImpl(
				servletContext,
				request,
				response,
				out,
				element
			);
		} finally {
			if(out != null) out.close();
		}
		writeMe = out==null ? null : out.getResult();
	}

	@Override
	public void writeTo(Writer out, ElementContext context) throws IOException {
		if(writeMe != null) writeMe.writeTo(out);
	}
}
