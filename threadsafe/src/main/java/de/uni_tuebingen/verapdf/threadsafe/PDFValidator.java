package de.uni_tuebingen.verapdf.threadsafe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.verapdf.core.VeraPDFException;
import org.verapdf.features.FeatureExtractorConfig;
import org.verapdf.features.FeatureFactory;
import org.verapdf.features.FeatureObjectType;
import org.verapdf.metadata.fixer.FixerFactory;
import org.verapdf.metadata.fixer.MetadataFixerConfig;
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.validation.validators.ValidatorConfig;
import org.verapdf.pdfa.validation.validators.ValidatorFactory;
import org.verapdf.policy.PolicyChecker;
import org.verapdf.processor.BatchProcessor;
import org.verapdf.processor.FormatOption;
import org.verapdf.processor.ProcessorConfig;
import org.verapdf.processor.ProcessorFactory;
import org.verapdf.processor.TaskType;
import org.verapdf.processor.plugins.PluginsCollectionConfig;


/**
 * Wrapper for veraPDF. Needed since there's no support for multiple instances and the veraPDF Batch
 * Api is not threadsafe.
 * 
 * @author Fabian Hamm
 *
 */
public class PDFValidator {
	private ByteArrayOutputStream xmlReport;
	private ByteArrayOutputStream htmlReport;
	private ValidatorConfig validatorConfig;
	private FeatureExtractorConfig featureConfig;
	private PluginsCollectionConfig pluginsConfig;
	private MetadataFixerConfig fixerConfig;
	private Integer maxFailuresDisplayed;
	private EnumSet<TaskType> tasks;

	private synchronized PDFValidator initialize(PDFAFlavour flavour, int maxFailures, int maxFailuresDisplayed) {
		setValidatorConfig(flavour, maxFailures, maxFailuresDisplayed);

		EnumSet<FeatureObjectType> featureSet = EnumSet.of(FeatureObjectType.ANNOTATION,
				FeatureObjectType.DOCUMENT_SECURITY, FeatureObjectType.EMBEDDED_FILE, FeatureObjectType.FONT,
				FeatureObjectType.INFORMATION_DICTIONARY, FeatureObjectType.ACTION, FeatureObjectType.COLORSPACE);
		featureConfig = FeatureFactory.configFromValues(featureSet);
		pluginsConfig = PluginsCollectionConfig.defaultConfig();
		fixerConfig = FixerFactory.defaultConfig();
		tasks = EnumSet.noneOf(TaskType.class);
		// tasks.add(TaskType.PARSE);
		tasks.add(TaskType.VALIDATE);
		tasks.add(TaskType.EXTRACT_FEATURES);

		return this;
	}

	/**
	 * 
	 * @param flavour
	 * @param maxFailures
	 * @return
	 */
	private synchronized PDFValidator setValidatorConfig(PDFAFlavour flavour, int maxFailures,
			int maxFailuresDisplayed) {
		if (flavour == null)
			flavour = PDFAFlavour.NO_FLAVOUR;
		if (maxFailures <= -1) {
			maxFailures = -1;
		}
		if (maxFailuresDisplayed <= -1) {
			maxFailuresDisplayed = -1;
		}
		this.maxFailuresDisplayed = maxFailuresDisplayed;
		validatorConfig = ValidatorFactory.createConfig(flavour, false, maxFailures);
		return this;
	}


	/**
	 * Standard batch validation process.
	 * 
	 * @param files
	 *            List of local files to be validated
	 * @return The PDFValidator instance being used
	 * @throws VeraPDFException
	 *             Exception during parsing or validation
	 * @throws IOException
	 *             File handling specific exception
	 */
	public synchronized void validateFile(File file, String validationFilePath, int maxFailures,
			int maxFailuresDisplayed, String validationProfileCode)
			throws VeraPDFException, IOException, SecurityException {
		VeraGreenfieldFoundryProvider.initialise();
		initialize(PDFAFlavour.fromString(validationProfileCode), maxFailures, maxFailuresDisplayed);

		try (FileOutputStream fileOut = new FileOutputStream(new File(validationFilePath))) {
			ProcessorConfig processorConfig = ProcessorFactory.fromValues(validatorConfig, featureConfig, pluginsConfig,
					fixerConfig, tasks);
			try (BatchProcessor processor = ProcessorFactory.fileBatchProcessor(processorConfig);) {
				List<File> files = new ArrayList<>();
				files.add(file);
				processor.process(files, ProcessorFactory.getHandler(FormatOption.MRR, true, fileOut,
						maxFailuresDisplayed, processorConfig.getValidatorConfig().isRecordPasses()));
			}
		}
	}


	public void applyPolicy(File policyFile, File xmlReport) throws VeraPDFException, IOException {
		File tempDir = new File(xmlReport.getParentFile() + File.separator + UUID.randomUUID());
		File policyOutputFile = new File(tempDir.getAbsolutePath() + File.separator + UUID.randomUUID() + ".xml");
		File mergedOutputFile = new File(tempDir.getAbsolutePath() + File.separator + UUID.randomUUID() + ".xml");
		FileUtils.touch(policyOutputFile);
		FileUtils.touch(mergedOutputFile);

		try (FileOutputStream schemaOut = new FileOutputStream(policyOutputFile);
				FileInputStream xmlIn = new FileInputStream(xmlReport);
				FileOutputStream mergedOut = new FileOutputStream(mergedOutputFile);) {
			PolicyChecker.applyPolicy(policyFile, xmlIn, schemaOut);
			schemaOut.flush();
			PolicyChecker.insertPolicyReport(policyOutputFile, xmlReport, mergedOut);
			mergedOut.flush();

			FileUtils.copyFile(mergedOutputFile, xmlReport, true);
		} finally {
			FileUtils.deleteQuietly(policyOutputFile);
			FileUtils.deleteQuietly(mergedOutputFile);
			FileUtils.deleteQuietly(tempDir);
		}
	}


	public ByteArrayOutputStream getXmlReport() {
		return xmlReport;
	}

	public void setXmlReport(ByteArrayOutputStream xmlReport) {
		this.xmlReport = xmlReport;
	}

	public ByteArrayOutputStream getHtmlReport() {
		return htmlReport;
	}

	public void setHtmlReport(ByteArrayOutputStream htmlReport) {
		this.htmlReport = htmlReport;
	}

	public Integer getMaxFailuresDisplayed() {
		return maxFailuresDisplayed;
	}

	public void setMaxFailuresDisplayed(Integer maxFailuresDisplayed) {
		this.maxFailuresDisplayed = maxFailuresDisplayed;
	}

}
