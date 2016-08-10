/*
 * ao-web-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
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
import com.semanticcms.core.servlet.OpenFile;
import com.semanticcms.dia.model.DiaExport;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * @see  OpenFile.FileOpener
 */
@WebListener(
	"Registers a file opener for all *." + DiaExport.EXTENSION + " files."
)
public class DiaFileOpener implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		OpenFile.addFileOpener(
			sce.getServletContext(),
			// Java 1.8: Lambda here
			new OpenFile.FileOpener() {
				@Override
				public String[] getCommand(File resourceFile) throws IOException {
					return new String[] {
						DiaImpl.getDiaOpenPath(),
						resourceFile.getCanonicalPath()
					};
				}
			},
			DiaExport.EXTENSION
		);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		OpenFile.removeFileOpener(
			sce.getServletContext(),
			DiaExport.EXTENSION
		);
	}
}
