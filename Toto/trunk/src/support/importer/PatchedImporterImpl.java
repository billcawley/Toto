package support.importer;

import org.zkoss.zss.api.impl.ImporterImpl;

public class PatchedImporterImpl extends ImporterImpl {

	public PatchedImporterImpl() {
		super(new PatchedImportAdapter());
	}
}
