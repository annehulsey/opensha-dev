package scratch.anne.risk_system_va;

import java.nio.file.*;

public class ReadLibrary_Example {

    public static void main(String[] args) throws Exception {
        // Create Path to a file
    	// json option
        Path filePath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\vulnerabilities.json");
//        // csv option
//        Path filePath = Paths.get("C:\\Users\\ahulsey\\git\\opensha-dev\\src\\main\\resources\\scratch\\anne\\risk_system_va\\vulnerabilities.csv");

        // Read the library
        VulnerabilityLibrary library = VulnerabilityLibraryReader.readLibrary(
            filePath,
            "library source",
            "description",
            "DR → fraction, IM → g",
            "date, author, workflow v1.0"
        );
        
        // Print library metadata
        System.out.println(library);
        System.out.println();
        System.out.println();

        // Get a specific vulnerability
        VulnerabilityModel vuln = library.getVulnerability("W1-lowrise");
        System.out.println(vuln);
        System.out.println();
        System.out.println(vuln.toVerboseString());
        
        // Get another specific vulnerability
        vuln = library.getVulnerability("W4-lowrise");
        System.out.println(vuln);
        System.out.println();
        System.out.println(vuln.toVerboseString());
        
    }
}
