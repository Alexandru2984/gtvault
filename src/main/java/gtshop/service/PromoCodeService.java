package gtshop.service;

import gtshop.model.PromoCode;

public interface PromoCodeService {
    PromoValidationResult validatePromo(String code, int orderTotal);
    int applyPromoAtCheckout(String code, int orderTotal);

    class PromoValidationResult {
        private boolean valid;
        private String code;
        private int discountPoints;
        private int finalTotal;
        private String message;

        public PromoValidationResult(boolean valid, String code, int discountPoints, int finalTotal, String message) {
            this.valid = valid;
            this.code = code;
            this.discountPoints = discountPoints;
            this.finalTotal = finalTotal;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getCode() {
            return code;
        }

        public int getDiscountPoints() {
            return discountPoints;
        }

        public int getFinalTotal() {
            return finalTotal;
        }

        public String getMessage() {
            return message;
        }
    }
}
