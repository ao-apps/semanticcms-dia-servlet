/*
 * semanticcms-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2021, 2022  AO Industries, Inc.
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
 * along with semanticcms-dia-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.dia.servlet.impl;

import java.io.File;

public final class DiaExport {

	private final File tmpFile;
	private final int width;
	private final int height;

	DiaExport(
		File tmpFile,
		int width,
		int height
	) {
		this.tmpFile = tmpFile;
		this.width = width;
		this.height = height;
	}

	public File getTmpFile() {
		return tmpFile;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
