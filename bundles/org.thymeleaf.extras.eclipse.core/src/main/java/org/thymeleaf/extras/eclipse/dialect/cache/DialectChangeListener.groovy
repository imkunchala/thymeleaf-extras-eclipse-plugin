/*
 * Copyright 2013, The Thymeleaf Emanuel Rabina (http://www.ultraq.net.nz/)
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

package org.thymeleaf.extras.eclipse.dialect.cache;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.thymeleaf.extras.eclipse.dialect.SingleFileDialectLocator;
import org.thymeleaf.extras.eclipse.dialect.XmlDialectLoader;
import org.thymeleaf.extras.eclipse.dialect.xml.Dialect;
import org.thymeleaf.extras.eclipse.dialect.xml.DialectItem;
import static org.eclipse.core.resources.IResourceChangeEvent.*;
import static org.thymeleaf.extras.eclipse.CorePlugin.*;
import static org.thymeleaf.extras.eclipse.dialect.cache.DialectItemProcessor.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A resource change listener, acting on changes made to any dialect files,
 * updating the entries in the dialect tree as necessary.
 * 
 * @author Emanuel Rabina
 */
public class DialectChangeListener implements IResourceChangeListener {

	private final ExecutorService resourcechangeexecutor = Executors.newSingleThreadExecutor();

	// Collection of dialect files that will be watched for updates to keep the cache up-to-date
	private final ConcurrentHashMap<IPath,IProject> dialectfilepaths =
			new ConcurrentHashMap<IPath,IProject>();

	private final XmlDialectLoader xmldialectloader;
	private final DialectTree dialecttree;

	/**
	 * Package-only constructor, watch over the given dialect tree.
	 * 
	 * @param xmldialectloader
	 * @param dialecttree
	 */
	DialectChangeListener(XmlDialectLoader xmldialectloader, DialectTree dialecttree) {

		this.xmldialectloader = xmldialectloader;
		this.dialecttree      = dialecttree;
	}

	/**
	 * When notified of a resource change, redirect the work to the change
	 * executor thread so as to not block the event change thread.
	 */
	@Override
	public void resourceChanged(final IResourceChangeEvent event) {

		resourcechangeexecutor.execute(new Runnable() {
			@Override
			public void run() {

				switch (event.getType()) {

				// If a dialect file has changed, update the dialect items associated with it
				case POST_CHANGE:
					IResourceDelta delta = event.getDelta();
					for (IPath dialectfilepath: dialectfilepaths.keySet()) {
						IResourceDelta dialectfiledelta = delta.findMember(dialectfilepath);
						if (dialectfiledelta != null) {
							logInfo("Dialect file " + dialectfilepath.lastSegment() +
									" changed, reloading dialect");
							IProject dialectfileproject = dialectfiledelta.getResource().getProject();
							IJavaProject javaproject = JavaCore.create(dialectfileproject);

							List<Dialect> updatedialect = xmldialectloader.loadDialects(
									new SingleFileDialectLocator(dialectfilepath));
							List<DialectItem> updateddialectitems = processDialectItems(
									updatedialect.get(0), javaproject);
							dialecttree.updateDialect(dialectfilepath, updateddialectitems);
						}
					}
					break;

				// If a project containing a dialect is changing, remove the dialect
				// from the dialect tree.
				case PRE_CLOSE:
				case PRE_DELETE:
					IProject project = (IProject)event.getResource();
					for (IPath dialectfilepath: dialectfilepaths.keySet()) {
						IProject dialectproject = dialectfilepaths.get(dialectfilepath);
						if (project.equals(dialectproject)) {
							logInfo("Project containing dialect file " + dialectfilepath.lastSegment() +
									" has been closed/deleted, removing dialect.");
							dialecttree.updateDialect(dialectfilepath, null);
							dialectfilepaths.remove(dialectfilepath);
						}
					}
					break;
				}
			}
		});
	}

	/**
	 * Stops the resource change executor.
	 */
	void shutdown() {

		resourcechangeexecutor.shutdown();
		try {
			if (resourcechangeexecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				resourcechangeexecutor.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			// Do nothing
		}
	}

	/**
	 * Track a dialect file for changes.
	 * 
	 * @param dialectfilepath
	 * @param project
	 */
	void trackDialectFileForChanges(IPath dialectfilepath, IJavaProject project) {

		dialectfilepaths.put(dialectfilepath, project.getProject());
	}
}
