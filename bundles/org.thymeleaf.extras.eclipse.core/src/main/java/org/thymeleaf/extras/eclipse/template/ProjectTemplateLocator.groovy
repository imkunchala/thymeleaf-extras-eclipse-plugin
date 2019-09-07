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

package org.thymeleaf.extras.eclipse.template

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.thymeleaf.extras.eclipse.scanner.ResourceLocator
import static org.thymeleaf.extras.eclipse.CorePlugin.*

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Locates Thymeleaf templates in the current project.  Basically, all HTML
 * files.
 * 
 * @author Emanuel Rabina
 */
class ProjectTemplateLocator implements ResourceLocator<IFile> {

	private static final String HTML_FILE_EXTENSION = ".html"

	private final IJavaProject project

	/**
	 * Constructor, sets the project to scan for templates.
	 * 
	 * @param project
	 */
	ProjectTemplateLocator(IJavaProject project) {

		this.project = project
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	List<IFile> locateResources() {

		logInfo("Scanning for Thymeleaf templates in the project")
		long start = System.currentTimeMillis()

		ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
		final ArrayList<IFile> templateStreams = new ArrayList<IFile>()

		try {
			// Multi-threaded search for template files - there can be a lot of files to get through
			ArrayList<Future<List<IFile>>> scannerTasks = new ArrayList<Future<List<IFile>>>()

			scanContainer(project.project, scannerTasks, executorService)

			// Collect all file results
			for (Future<List<IFile>> scannerTask: scannerTasks) {
				try {
					for (IFile file: scannerTask.get()) {
						templateStreams.add(file)
					}
				}
				catch (ExecutionException ex) {
					logError("Unable to execute scanning task", ex)
				}
				catch (InterruptedException ex) {
					logError("Unable to execute scanning task", ex)
				}
			}
		}
		finally {
			executorService.shutdown()
			try {
				if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
					executorService.shutdownNow()
				}
			}
			catch (Exception ex) {
				logError('An error occured while attempting to shut down the project locator executor service', ex)
			}
		}

		logInfo("Scanning complete.  Execution time: ${System.currentTimeMillis() - start} ms")
		return templateStreams
	}

	/**
	 * Recursive scan of a container resource (currently only folders and
	 * projects), searches for files to load.
	 * 
	 * TODO: Make this method contain all of the multi-threaded execution so
	 *       that we don't have to share the executor service across methods in
	 *       this class.
	 * 
	 * @param container
	 * @param executorService
	 */
	private static void scanContainer(final IContainer container,
		final ArrayList<Future<List<IFile>>> scannerTasks, final ExecutorService executorService) {

		// Projects and folders
		if (container instanceof IProject || container instanceof IFolder) {
			scannerTasks.add(executorService.submit(new Callable<List<IFile>>() {
				@Override
				public List<IFile> call() throws Exception {

					ArrayList<IFile> files = new ArrayList<IFile>()
					for (IResource resource: container.members()) {

						// Recurse folder scanning
						if (resource instanceof IContainer) {
							scanContainer((IContainer)resource, scannerTasks, executorService)
						}

						// Accept files
						else if (resource instanceof IFile) {
							IFile file = (IFile)resource
							if (file.getName().endsWith(HTML_FILE_EXTENSION)) {
								files.add(file)
							}
						}
					}
					return files
				}
			}))
		}
	}
}
