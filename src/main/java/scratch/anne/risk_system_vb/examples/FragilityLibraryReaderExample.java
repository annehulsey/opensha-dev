package scratch.anne.risk_system_vb.examples;

import java.nio.file.Path;

import scratch.anne.risk_system_vb.io.FragilityLibraryReader;
import scratch.anne.risk_system_vb.structural_response.ResponseModelLibrary;
import scratch.anne.risk_system_vb.structural_response.fragilities.FragilityModel;

public class FragilityLibraryReaderExample {

    public static void main(String[] args) throws Exception {
    	
        // Create Path to a file
    	Path resourceFolder = Path.of("C:/Users/ahulsey/git/opensha-dev/src/main/resources/scratch/anne/risk_system_vb");
        Path fragLibraryJSON = resourceFolder.resolve("fragilities.json");
        Path fragLibraryCSV  = resourceFolder.resolve("fragilities.csv");
        
        Path filePath = fragLibraryJSON;
//        Path filePath = fragLibraryCSV;

        // Read the library
        ResponseModelLibrary<FragilityModel> library = FragilityLibraryReader.readLibrary(
            filePath,
            "library source",
            "description",
            "date, author, workflow v1.0"
        );
        
        // Print library metadata
        System.out.println(library);
        System.out.println();
        System.out.println();

        // Get a specific vulnerability
        FragilityModel frag = library.getModelByName("Fragility_A");
        System.out.println(frag);
        System.out.println();
        System.out.println(frag.toVerboseString());
        
        // Get another specific vulnerability
        frag = library.getModelByName("Fragility_C");
        System.out.println(frag);
        System.out.println();
        System.out.println(frag.toVerboseString());
        
    }
}
