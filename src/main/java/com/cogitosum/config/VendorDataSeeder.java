package com.cogitosum.config;

import com.cogitosum.entity.Vendor;
import com.cogitosum.repository.VendorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed.vendor-data.enabled", havingValue = "true")
public class VendorDataSeeder implements CommandLineRunner {
    private final VendorRepository vendors;
    private final BootstrapCompanyProvider bootstrapCompanyProvider;
    private com.cogitosum.entity.Company company;

    public VendorDataSeeder(VendorRepository vendors, BootstrapCompanyProvider bootstrapCompanyProvider) {
        this.vendors = vendors;
        this.bootstrapCompanyProvider = bootstrapCompanyProvider;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        company = bootstrapCompanyProvider.requireBootstrapCompany();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/ListVendors.csv").getInputStream(), Charset.forName("windows-1252")))) {
            List<String> header = parse(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parse(line);
                String name = value(values, header, "Vendor");
                if (name.isBlank() || !"Active".equalsIgnoreCase(value(values, header, "Active Status"))) continue;
                if (vendors.findByCompanyIdAndBusinessNameIgnoreCase(company.getId(), name).isPresent()) continue;

                String email = value(values, header, "Main Email");
                String address = value(values, header, "Bill from 1");
                Vendor vendor = new Vendor();
                vendor.setCompany(company);
                vendor.setName(value(values, header, "Primary Contact"));
                if (vendor.getName().isBlank()) vendor.setName(name);
                vendor.setBusinessName(name);
                vendor.setEmail(email.isBlank() ? uniquePlaceholderEmail(name) : email);
                vendor.setAddress(address.isBlank() ? "Address not provided" : address);
                vendor.setCity("Not provided");
                vendor.setProvince("QC");
                vendor.setPostalCode("H0H 0H0");
                vendor.setCountry("Canada");
                vendors.save(vendor);
            }
        }
    }

    private String uniquePlaceholderEmail(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.|\\.$", "");
        String email = (base.isBlank() ? "vendor" : base) + "@imported.local";
        int suffix = 2;
        while (vendors.findByCompanyIdAndEmail(company.getId(), email).isPresent()) email = base + suffix++ + "@imported.local";
        return email;
    }

    private static String value(List<String> values, List<String> header, String name) {
        int index = header.indexOf(name);
        return index >= 0 && index < values.size() && values.get(index) != null ? values.get(index).trim() : "";
    }

    private static List<String> parse(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') field.append('"');
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                result.add(field.toString());
                field.setLength(0);
            } else field.append(c);
        }
        result.add(field.toString());
        return result;
    }
}
