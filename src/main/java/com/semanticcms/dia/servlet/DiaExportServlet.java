/*
 * semanticcms-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017  AO Industries, Inc.
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

import com.aoindustries.io.FileUtils;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.repository.Book;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.dia.model.Dia;
import com.semanticcms.dia.servlet.impl.DiaExport;
import com.semanticcms.dia.servlet.impl.DiaImpl;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(DiaExportServlet.SERVLET_PATH+"/*")
public class DiaExportServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/semanticcms-dia-servlet/dia-export";

	/**
	 * Gets the dia export or null when not found.
	 */
	private DiaExport getThumbnail(HttpServletRequest request) throws IOException, ServletException {
		// pathInfo must be present
		String pathInfo = request.getPathInfo();
		if(pathInfo == null) return null;
		// Must end in expected extension
		if(!pathInfo.endsWith(DiaImpl.PNG_EXTENSION)) return null;
		// Find height
		int dimSepPos = pathInfo.lastIndexOf(DiaImpl.DIMENSION_SEPARATOR, pathInfo.length() - DiaImpl.PNG_EXTENSION.length() - 1);
		if(dimSepPos == -1) return null;
		String heightStr = pathInfo.substring(dimSepPos+1, pathInfo.length() - DiaImpl.PNG_EXTENSION.length());
		//log("heightStr=" +heightStr);
		Integer height;
		if(heightStr.length()==1 && heightStr.charAt(0) == DiaImpl.EMPTY_SIZE) {
			height = null;
		} else {
			try {
				height = Integer.parseInt(heightStr);
			} catch(NumberFormatException e) {
				return null;
			}
		}
		//log("height=" +height);
		// Find width
		int sizeSepPos = pathInfo.lastIndexOf(DiaImpl.SIZE_SEPARATOR, dimSepPos-1);
		if(sizeSepPos == -1) return null;
		String widthStr = pathInfo.substring(sizeSepPos+1, dimSepPos);
		//log("widthStr=" +widthStr);
		Integer width;
		if(widthStr.length()==1 && widthStr.charAt(0) == DiaImpl.EMPTY_SIZE) {
			width = null;
		} else {
			try {
				width = Integer.parseInt(widthStr);
			} catch(NumberFormatException e) {
				return null;
			}
		}
		//log("width=" +width);
		// Must have at least width or height to continue
		if(width==null && height==null) return null;
		// Find book and path
		ResourceRef resourceRef;
		{
			String combinedPath = pathInfo.substring(0, sizeSepPos) + Dia.DOT_EXTENSION;
			Book book = SemanticCMS.getInstance(getServletContext()).getPublishedBook(combinedPath);
			if(book == null) return null;
			BookRef bookRef = book.getBookRef();
			String prefix = bookRef.getPrefix();
			assert combinedPath.startsWith(prefix);
			resourceRef = new ResourceRef(
				bookRef,
				combinedPath.substring(prefix.length())
			);
		}

		// Get the thumbnail image
		try {
			return DiaImpl.exportDiagram(
				getServletContext(),
				resourceRef,
				width,
				height,
				(File)getServletContext().getAttribute("javax.servlet.context.tempdir" /*ServletContext.TEMPDIR*/)
			);
		} catch(InterruptedException e) {
			// Restore the interrupted status
			Thread.currentThread().interrupt();
			throw new ServletException(e);
		} catch(FileNotFoundException e) {
			return null;
		}
	}

	@Override
	protected long getLastModified(HttpServletRequest request) {
		try {
			DiaExport thumbnail = getThumbnail(request);
			if(thumbnail == null) {
				return -1;
			} else {
				long lastModified = thumbnail.getTmpFile().lastModified();
				return lastModified==0 ? -1 : lastModified;
			}
		} catch(IOException e) {
			getServletContext().log(null, e);
			return -1;
		} catch(ServletException e) {
			getServletContext().log(null, e);
			return -1;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		DiaExport thumbnail = getThumbnail(request);
		if(thumbnail == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} else {
			// Write output
			response.resetBuffer();
			response.setContentType("image/png");
			long length = thumbnail.getTmpFile().length();
			if(length>0 && length<=Integer.MAX_VALUE) response.setContentLength((int)length);
			OutputStream out = response.getOutputStream();
			FileUtils.copy(thumbnail.getTmpFile(), out);
		}
	}
}
