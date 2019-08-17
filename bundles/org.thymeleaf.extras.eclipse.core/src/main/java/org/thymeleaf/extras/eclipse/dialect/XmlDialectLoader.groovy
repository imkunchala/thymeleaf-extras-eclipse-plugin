/*
 * Copyright 2013, The Thymeleaf Project (http://www.thymeleaf.org/)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thymeleaf.extras.eclipse.dialect;

import nz.net.ultraq.jaxb.XmlReader;

import org.thymeleaf.extras.eclipse.dialect.xml.Dialect;
import org.thymeleaf.extras.eclipse.dialect.xml.DialectItem;
import static org.thymeleaf.extras.eclipse.CorePlugin.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads dialect help/documentation XML files from those returned by a
 * {@link DialectLocator}.
 * 
 * @author Emanuel Rabina
 */
public class XmlDialectLoader implements DialectLoader<InputStream> {

	private static final XmlReader<Dialect> xmlReader = new XmlReader<Dialect>(Dialect.class);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Dialect> loadDialects(DialectLocator<InputStream> locator) {

		ArrayList<Dialect> dialects = new ArrayList<Dialect>();

		// TODO: Replace with something that can use try-with-resources
		for (InputStream dialectfilestream: locator.locateDialects()) {

			// Link processors and expression objects/methods with their dialect
			try {
				Dialect dialect = xmlReader.read(dialectfilestream);
				for (DialectItem dialectitem: dialect.getDialectItems()) {
					dialectitem.setDialect(dialect);
				}
				dialects.add(dialect);
			}
			catch (Exception ex) {
				logError("Error reading the dialect file", ex);
			}
			finally {
				try {
					dialectfilestream.close();
				}
				catch (IOException ex) {
					logError("Unable to close dialect file input stream", ex);
				}
			}
		}

		return dialects;
	}
}
