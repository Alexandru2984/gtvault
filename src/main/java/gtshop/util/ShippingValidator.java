package gtshop.util;

import gtshop.web.ApiException;
import java.util.List;
import java.util.regex.Pattern;

/** Validarea datelor de livrare pentru premiile fizice (adrese din Romania). */
public final class ShippingValidator {

    public record Shipping(String name, String phone, String address, String city, String county, String postalCode) {}

    public static final List<String> COUNTIES = List.of(
            "Alba", "Arad", "Argeș", "Bacău", "Bihor", "Bistrița-Năsăud", "Botoșani", "Brașov", "Brăila", "București",
            "Buzău", "Caraș-Severin", "Călărași", "Cluj", "Constanța", "Covasna", "Dâmbovița", "Dolj", "Galați",
            "Giurgiu", "Gorj", "Harghita", "Hunedoara", "Ialomița", "Iași", "Ilfov", "Maramureș", "Mehedinți", "Mureș",
            "Neamț", "Olt", "Prahova", "Satu Mare", "Sălaj", "Sibiu", "Suceava", "Teleorman", "Timiș", "Tulcea",
            "Vaslui", "Vâlcea", "Vrancea");

    private static final Pattern PHONE = Pattern.compile("^(\\+40|0040|0)7\\d{8}$");
    private static final Pattern POSTAL = Pattern.compile("^\\d{6}$");

    private ShippingValidator() {
    }

    public static Shipping validate(Shipping raw) {
        if (raw == null) {
            throw ApiException.badRequest("Comanda conține produse fizice: completează datele de livrare.");
        }
        String name = InputSanitizer.cleanText(raw.name(), 3, 80, "Numele destinatarului");
        if (name.contains("<") || name.contains(">")) {
            throw ApiException.badRequest("Numele destinatarului conține caractere nepermise.");
        }
        String phone = raw.phone() == null ? "" : raw.phone().replaceAll("[\\s().-]", "");
        if (!PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("Numărul de telefon trebuie să fie un mobil românesc valid (ex: 07xx xxx xxx).");
        }
        if (phone.startsWith("+40")) {
            phone = "0" + phone.substring(3);
        } else if (phone.startsWith("0040")) {
            phone = "0" + phone.substring(4);
        }
        String address = InputSanitizer.cleanText(raw.address(), 5, 200, "Adresa").replace('\n', ' ');
        String city = InputSanitizer.cleanText(raw.city(), 2, 80, "Localitatea");
        String county = COUNTIES.stream()
                .filter(c -> c.equalsIgnoreCase(raw.county() == null ? "" : raw.county().trim()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Selectează un județ valid."));
        String postal = raw.postalCode() == null ? "" : raw.postalCode().trim();
        if (!POSTAL.matcher(postal).matches()) {
            throw ApiException.badRequest("Codul poștal trebuie să aibă 6 cifre.");
        }
        return new Shipping(name, phone, address, city, county, postal);
    }
}
