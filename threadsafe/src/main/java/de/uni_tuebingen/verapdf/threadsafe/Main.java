package de.uni_tuebingen.verapdf.threadsafe;

import java.io.File;

public class Main {

	public static void main(String[] args) {

		if (args.length < 5 || args.length > 6)
			System.exit(1);

		try {
			String filePath = args[0];
			String reportFilePath = args[1];
			int maxFailures = Integer.valueOf(args[2]);
			int maxFailuresDisplayed = Integer.valueOf(args[3]);
			String validationProfileCode = args[4];
			String policyFilePath = args.length == 6 ? args[5] : null;

			PDFValidator validator = new PDFValidator();
			validator.validateFile(new File(filePath), reportFilePath, maxFailures, maxFailuresDisplayed,
					validationProfileCode);

			if (policyFilePath != null)
				validator.applyPolicy(new File(policyFilePath), new File(reportFilePath));

		} catch (Exception e) {
			System.exit(1);
		}
	}
}
