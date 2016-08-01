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

import com.aoindustries.awt.image.ImageSizeCache;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.FileUtils;
import com.aoindustries.lang.ProcessResult;
import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.util.Sequence;
import com.aoindustries.util.UnsynchronizedSequence;
import com.aoindustries.web.dia.DiaExport;
import com.aoindustries.web.dia.servlet.DiaExportServlet;
import com.aoindustries.web.page.PageRef;
import com.aoindustries.web.page.servlet.CaptureLevel;
import com.aoindustries.web.page.servlet.OpenFile;
import com.aoindustries.web.page.servlet.PageRefResolver;
import com.aoindustries.web.page.servlet.impl.LinkImpl;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class DiaImpl {

	private static final String LINUX_DIA_PATH = "/usr/bin/dia";

	private static final String WINDOWS_DIA_PATH = "C:\\Program Files (x86)\\Dia\\bin\\dia.exe";

	private static final String WINDOWS_DIAW_PATH = "C:\\Program Files (x86)\\Dia\\bin\\diaw.exe";

	private static final String TEMP_SUBDIR = DiaExport.class.getName();

	private static final String MISSING_IMAGE_PATH = "/ao-web-dia-servlet/images/missing-image.jpg";
	private static final int MISSING_IMAGE_WIDTH = 225;
	private static final int MISSING_IMAGE_HEIGHT = 224;

	public static final char SIZE_SEPARATOR = '-';
	public static final char EMPTY_SIZE = '_';
	public static final char DIMENSION_SEPARATOR = 'x';
	public static final String PNG_EXTENSION = ".png";

	/**
	 * The request key used to ensure per-request unique element IDs.
	 */
	private static final String ID_SEQUENCE_REQUEST_ATTRIBUTE_NAME = DiaImpl.class.getName() + ".idSequence";

	/**
	 * The alt link ID prefix.
	 */
	private static final String ALT_LINK_ID_PREFIX = "ao-web-dia-servlet-alt-pixel-ratio-";

	/**
	 * The default width when neither width nor height provided.
	 */
	private static final int DEFAULT_WIDTH = 200;

	/**
	 * The supported pixel densities, these must be ordered from lowest to highest.  The first is the default.
	 */
	private static final int[] PIXEL_DENSITIES = {
		1,
		2,
		3,
		4
	};

	private static String getDiaExportPath() {
		if(OpenFile.isWindows()) {
			return WINDOWS_DIA_PATH;
		} else {
			return LINUX_DIA_PATH;
		}
	}

	public static String getDiaOpenPath() {
		if(OpenFile.isWindows()) {
			return WINDOWS_DIAW_PATH;
		} else {
			return LINUX_DIA_PATH;
		}
	}

	public static DiaExport exportDiagram(
		PageRef pageRef,
		Integer width,
		Integer height,
		File tmpDir
	) throws FileNotFoundException, IOException {
		File diaFile = pageRef.getResourceFile(true, true);

		String diaPath = pageRef.getPath();
		// Strip extension if matches expected value
		if(diaPath.toLowerCase(Locale.ROOT).endsWith(DiaExport.DOT_EXTENSION)) {
			diaPath = diaPath.substring(0, diaPath.length() - DiaExport.DOT_EXTENSION.length());
		}
		// Generate the temp filename
		File tmpFile = new File(
			tmpDir,
			TEMP_SUBDIR
				+ pageRef.getBookPrefix().replace('/', File.separatorChar)
				+ diaPath.replace('/', File.separatorChar)
				+ "-"
				+ (width==null ? "_" : width.toString())
				+ "x"
				+ (height==null ? "_" : height.toString())
				+ ".png"
		);
		// Make temp directory if needed (and all parents)
		tmpDir = tmpFile.getParentFile();
		if(!tmpDir.exists()) FileUtils.mkdirs(tmpDir);
		// Re-export when missing or timestamps indicate needs recreated
		if(!tmpFile.exists() || diaFile.lastModified() >= tmpFile.lastModified()) {
			// Determine size for scaling
			final String sizeParam;
			if(width==null) {
				if(height==null) {
					sizeParam = null;
				} else {
					sizeParam = "x" + height;
				}
			} else {
				if(height==null) {
					sizeParam = width + "x";
				} else {
					sizeParam = width + "x" + height;
				}
			}
			// Build the command
			final String diaExePath = getDiaExportPath();
			final String[] command;
			if(sizeParam == null) {
				command = new String[] {
					diaExePath,
					"--export=" + tmpFile.getCanonicalPath(),
					"--filter=png",
					"--log-to-stderr",
					diaFile.getCanonicalPath()
				};
			} else {
				command = new String[] {
					diaExePath,
					"--export=" + tmpFile.getCanonicalPath(),
					"--filter=png",
					"--size=" + sizeParam,
					"--log-to-stderr",
					diaFile.getCanonicalPath()
				};
			}
			// Export using dia
			ProcessResult result = ProcessResult.exec(command);
			int exitVal = result.getExitVal();
			if(exitVal != 0) throw new IOException(diaExePath + ": non-zero exit value: " + exitVal);
			if(!OpenFile.isWindows()) {
				// Dia does not set non-zero exit value, instead, it writes both errors and normal output to stderr
				// (Dia version 0.97.2, compiled 23:51:04 Apr 13 2012)
				String normalOutput = diaFile.getCanonicalPath() + " --> " + tmpFile.getCanonicalPath();
				// Read the standard error, if any one line matches the expected line, then it is OK
				// other lines include stuff like: Xlib:  extension "RANDR" missing on display ":0".
				boolean foundNormalOutput = false;
				String stderr = result.getStderr();
				try (BufferedReader errIn = new BufferedReader(new StringReader(stderr))) {
					String line;
					while((line = errIn.readLine())!=null) {
						if(line.equals(normalOutput)) {
							foundNormalOutput = true;
							break;
						}
					}
				}
				if(!foundNormalOutput) {
					throw new IOException(diaExePath + ": " + stderr);
				}
			}
		}
		// Get actual dimensions
		Dimension pngSize = ImageSizeCache.getImageSize(tmpFile);
		
		return new DiaExport(
			tmpFile,
			pngSize.width,
			pngSize.height
		);
	}

	private static String buildUrlPath(
		HttpServletRequest request,
		PageRef pageRef,
		int width,
		int height,
		int pixelDensity,
		DiaExport export
	) throws ServletException {
		String diaPath = pageRef.getPath();
		// Strip extension
		if(!diaPath.endsWith(DiaExport.DOT_EXTENSION)) throw new ServletException("Unexpected file extension for diagram: " + diaPath);
		diaPath = diaPath.substring(0, diaPath.length() - DiaExport.DOT_EXTENSION.length());
		StringBuilder urlPath = new StringBuilder();
		urlPath
			.append(request.getContextPath())
			.append(DiaExportServlet.SERVLET_PATH)
			.append(pageRef.getBookPrefix())
			.append(diaPath)
			.append(SIZE_SEPARATOR);
		if(width == 0) {
			urlPath.append(EMPTY_SIZE);
		} else {
			urlPath.append(width * pixelDensity);
		}
		urlPath.append(DIMENSION_SEPARATOR);
		if(height == 0) {
			urlPath.append(EMPTY_SIZE);
		} else {
			urlPath.append(height * pixelDensity);
		}
		urlPath.append(PNG_EXTENSION);
		// Check for header disabling auto last modified
		if(!"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))) {
			urlPath
				.append('?')
				.append(LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME)
				.append('=')
				.append(LastModifiedServlet.encodeLastModified(export.getTmpFile().lastModified()))
			;
		}
		return urlPath.toString();
	}

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
				if(width==0 && height==0) width = DEFAULT_WIDTH;
				File resourceFile = pageRef.getResourceFile(false, true);
				// Get the thumbnail image in default pixel density
				DiaExport export =
					resourceFile==null
					? null
					: exportDiagram(
						pageRef,
						width==0 ? null : (width * PIXEL_DENSITIES[0]),
						height==0 ? null : (height * PIXEL_DENSITIES[0]),
						(File)servletContext.getAttribute("javax.servlet.context.tempdir" /*ServletContext.TEMPDIR*/)
					)
				;
				// Find id sequence
				Sequence idSequence = (Sequence)request.getAttribute(ID_SEQUENCE_REQUEST_ATTRIBUTE_NAME);
				if(idSequence == null) {
					idSequence = new UnsynchronizedSequence();
					request.setAttribute(ID_SEQUENCE_REQUEST_ATTRIBUTE_NAME, idSequence);
				}
				// Write the img tag
				long imgId = idSequence.getNextSequenceValue();
				out.append("<img id=\"" + ALT_LINK_ID_PREFIX).append(Long.toString(imgId)).append("\" src=\"");
				final String urlPath;
				if(export != null) {
					urlPath = buildUrlPath(
						request,
						pageRef,
						width,
						height,
						PIXEL_DENSITIES[0],
						export
					);
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
						export!=null
						? (export.getWidth() / PIXEL_DENSITIES[0])
						: width!=0
						? width
						: (MISSING_IMAGE_WIDTH * height / MISSING_IMAGE_HEIGHT)
					),
					out
				);
				out.append("\" height=\"");
				encodeTextInXhtmlAttribute(
					Integer.toString(
						export!=null
						? (export.getHeight() / PIXEL_DENSITIES[0])
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

				if(export != null && PIXEL_DENSITIES.length > 1) {
					assert resourceFile != null;
					// Write links to the exports for higher pixel densities
					long[] altLinkNums = new long[PIXEL_DENSITIES.length - 1];
					for(int i=1; i<PIXEL_DENSITIES.length; i++) {
						int pixelDensity = PIXEL_DENSITIES[i];
						// Get the thumbnail image in alternate pixel density
						DiaExport altExport = exportDiagram(
							pageRef,
							width==0 ? null : (width * pixelDensity),
							height==0 ? null : (height * pixelDensity),
							(File)servletContext.getAttribute("javax.servlet.context.tempdir" /*ServletContext.TEMPDIR*/)
						);
						// Write the a tag to additional pixel densities
						out.append("<a id=\"" + ALT_LINK_ID_PREFIX);
						long altLinkNum = idSequence.getNextSequenceValue();
						altLinkNums[i - 1] = altLinkNum;
						out.append(Long.toString(altLinkNum));
						out.append("\" style=\"display:none\" href=\"");
						final String altUrlPath = buildUrlPath(
							request,
							pageRef,
							width,
							height,
							pixelDensity,
							altExport
						);
						encodeTextInXhtmlAttribute(
							response.encodeURL(
								UrlUtils.encodeUrlPath(
									altUrlPath,
									responseEncoding
								)
							),
							out
						);
						out.append("\">, x");
						encodeTextInXhtml(Integer.toString(pixelDensity), out);
						out.append("</a>");
					}
					// Write script to hide alt links and select best based on device pixel ratio
					out.append("<script type=\"text/javascript\">\n"
						+ "// <![CDATA[\n");
					// hide alt links
					//for(int i=1; i<PIXEL_DENSITIES.length; i++) {
					//	long altLinkNum = altLinkNums[i - 1];
					//	out
					//		.append("document.getElementById(\"" + ALT_LINK_ID_PREFIX)
					//		.append(Long.toString(altLinkNum))
					//		.append("\").style.display = \"none\";\n");
					//}
					// select best based on device pixel ratio
					out.append("if(window.devicePixelRatio) {\n");
					// out.append("\twindow.alert(\"devicePixelRatio=\" + window.devicePixelRatio);\n");
					for(int i=PIXEL_DENSITIES.length - 1; i >= 1; i--) {
						long altLinkNum = altLinkNums[i - 1];
						int pixelDensity = PIXEL_DENSITIES[i];
						out.append('\t');
						if(i != (PIXEL_DENSITIES.length - 1)) out.append("else ");
						out
							.append("if(window.devicePixelRatio >= ")
							.append(Integer.toString(pixelDensity))
							.append(") {\n"
								+ "\t\tdocument.getElementById(\"" + ALT_LINK_ID_PREFIX)
							.append(Long.toString(imgId))
							.append("\").src = document.getElementById(\"" + ALT_LINK_ID_PREFIX)
							.append(Long.toString(altLinkNum))
							.append("\").getAttribute(\"href\");\n"
								+ "\t}\n");
					}
					out.append("}\n"
						+ "// ]]>\n"
						+ "</script>");
				}
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private DiaImpl() {
	}
}
