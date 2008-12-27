/*
 *  This file is part of the OWASP Proxy, a free intercepting HTTP proxy
 *  library.
 *  Copyright (C) 2008  Rogan Dawes <rogan@dawes.za.net>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as 
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package org.owasp.proxy.io;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.owasp.proxy.io.ChunkedOutputStream;

public class ChunkedOutputStreamTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testWriteByteArrayIntInt() throws IOException {
		OutputStream out = new ChunkedOutputStream(System.out);
		out.write("ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes());
		out.close();
	}

}