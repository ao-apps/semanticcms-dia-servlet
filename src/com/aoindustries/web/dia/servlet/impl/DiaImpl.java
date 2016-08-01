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
package com.aoindustries.web.dia.servlet.impl;

import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.web.dia.DiaExport;
import com.aoindustries.web.dia.servlet.DiaExportServlet;
import com.aoindustries.web.page.PageRef;
import com.aoindustries.web.page.servlet.CaptureLevel;
import com.aoindustries.web.page.servlet.PageRefResolver;
import com.aoindustries.web.page.servlet.impl.LinkImpl;
import java.io.File;
import java.io.IOException;
import java.rmi.ServerException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class DiaImpl {

	private static final String MISSING_IMAGE_PATH = "/ao-web-dia-servlet/images/missing-image.jpg";
	private static final int MISSING_IMAGE_WIDTH = 225;
	private static final int MISSING_IMAGE_HEIGHT = 224;

	public static final char SIZE_SEPARATOR = '-';
	public static final char EMPTY_SIZE = '_';
	public static final char DIMENSION_SEPARATOR = 'x';
	public static final String PNG_EXTENSION = ".png";

	public static void writeDiaImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String book,
		String path,
		int width,
		int height
	) throws ServletException, IOException {
		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			PageRef pageRef = PageRefResolver.getPageRef(servletContext, request, book, path);
			if(captureLevel == CaptureLevel.BODY) {
				final String responseEncoding = response.getCharacterEncoding();
				// Use default width when neither provided
				if(width==0 && height==0) width = DiaExportServlet.DEFAULT_WIDTH;
				File resourceFile = pageRef.getResourceFile(false, true);
				// Get the thumbnail image
				DiaExport thumbnail =
					resourceFile==null
					? null
					: DiaExport.exportDiagram(
						pageRef,
						width==0 ? null : (width * DiaExportServlet.OVERSAMPLING),
						height==0 ? null : (height * DiaExportServlet.OVERSAMPLING),
						(File)servletContext.getAttribute("javax.servlet.context.tempdir" /*ServletContext.TEMPDIR*/)
					)
				;
				// Write the img tag
				out.append("<img src=\"");
				final String urlPath;
				if(thumbnail != null) {
					String diaPath = pageRef.getPath();
					// Strip extension
					if(!diaPath.endsWith(DiaExport.DIA_EXTENSION)) throw new ServerException("Unexpected file extension for diagram: " + diaPath);
					diaPath = diaPath.substring(0, diaPath.length() - DiaExport.DIA_EXTENSION.length());
					StringBuilder urlPathSB = new StringBuilder();
					urlPathSB
						.append(request.getContextPath())
						.append(DiaExportServlet.SERVLET_PATH)
						.append(pageRef.getBookPrefix())
						.append(diaPath)
						.append(SIZE_SEPARATOR);
					if(width == 0) {
						urlPathSB.append(EMPTY_SIZE);
					} else {
						urlPathSB.append(width * DiaExportServlet.OVERSAMPLING);
					}
					urlPathSB.append(DIMENSION_SEPARATOR);
					if(height == 0) {
						urlPathSB.append(EMPTY_SIZE);
					} else {
						urlPathSB.append(height * DiaExportServlet.OVERSAMPLING);
					}
					urlPathSB.append(PNG_EXTENSION);
					// Check for header disabling auto last modified
					if(!"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))) {
						urlPathSB
							.append('?')
							.append(LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME)
							.append('=')
							.append(LastModifiedServlet.encodeLastModified(thumbnail.getTmpFile().lastModified()))
						;
					}
					urlPath = urlPathSB.toString();
				} else {
					urlPath =
						request.getContextPath()
						+ MISSING_IMAGE_PATH
					;
				}
				encodeTextInXhtmlAttribute(
					response.encodeURL(
						UrlUtils.encodeUrlPath(
							urlPath,
							responseEncoding
						)
					),
					out
				);
				out.append("\" width=\"");
				encodeTextInXhtmlAttribute(
					Integer.toString(
						thumbnail!=null
						? (thumbnail.getWidth() / DiaExportServlet.OVERSAMPLING)
						: width!=0
						? width
						: (MISSING_IMAGE_WIDTH * height / MISSING_IMAGE_HEIGHT)
					),
					out
				);
				out.append("\" height=\"");
				encodeTextInXhtmlAttribute(
					Integer.toString(
						thumbnail!=null
						? (thumbnail.getHeight() / DiaExportServlet.OVERSAMPLING)
						: height!=0
						? height
						: (MISSING_IMAGE_HEIGHT * width / MISSING_IMAGE_WIDTH)
					),
					out
				);
				out.append("\" alt=\"");
				if(resourceFile == null) {
					LinkImpl.writeBrokenPathInXhtmlAttribute(pageRef, out);
				} else {
					encodeTextInXhtmlAttribute(resourceFile.getName(), out);
				}
				out.append("\" />");
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private DiaImpl() {
	}
}
