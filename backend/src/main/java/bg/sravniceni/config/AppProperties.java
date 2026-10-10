package bg.sravniceni.config;

import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Supabase supabase,
        Set<String> adminEmails,
        Cors cors,
        @Name("import") Import importer) {

    public AppProperties {
        adminEmails = adminEmails == null ? Set.of() : adminEmails;
    }

    public record Supabase(String url, String jwtSecret) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Настройки на импорта от kolkostruva.bg. */
    public record Import(String baseUrl, boolean enabled, String cron, String zone,
                         Set<String> chains, int historyDays) {

        public Import {
            chains = chains == null ? Set.of() : chains;
        }

        public boolean includesChain(String eik) {
            return chains.isEmpty() || chains.contains(eik);
        }
    }
}
