package scratch.anne.risk_system_va.portfolio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PortfolioReader {

    public static Portfolio readCSV(Path file) throws IOException {

        List<String> lines = Files.readAllLines(file);
        List<Asset> assets = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String[] t = lines.get(i).split(",");

            assets.add(new Asset(
                t[0],
                Double.parseDouble(t[1]),
                Double.parseDouble(t[2]),
                Double.parseDouble(t[3]),
                Double.parseDouble(t[4]),
                t[5]
            ));
        }

        return new Portfolio(assets,
            file.toString(),
            "value: USD, vs30: m/s",
            "vulnerability JSON",
            "WGS84",
            "portfolio description",
            "creation info");
    }
}
