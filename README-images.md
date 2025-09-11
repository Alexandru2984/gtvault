# GT Shop - Product Images System

## Overview

GT Shop folosește un sistem intelligent de imagini pentru produse care combină SVG-uri custom create cu imagini placeholder de pe Unsplash.

## Local SVG Images

Pentru produsele specifice GT Shop, am creat SVG-uri vectoriale custom care se potrivesc perfect cu identitatea brandului:

### Produse cu SVG Custom:
- **tricou** → `images/tricou.svg` - Tricou GT cu logo și gradient blue
- **sticker** → `images/sticker.svg` - Sticker colorat cu efecte vizuale
- **agenda** → `images/agenda.svg` - Agendă profesională cu linii și bookmark
- **mentorat** → `images/mentorat.svg` - Mentor cu laptop și speech bubble
- **mouse** → `images/mouse.svg` - Mouse gaming cu LED și logo GT
- **meet** → `images/meet.svg` - Ecran video call cu participanți
- **lucky** → `images/lucky.svg` - Roată norocoasă colorată cu premii
- **tracks** → `images/tracks.svg` - Trasee de învățare cu progress bar
- **cv** → `images/cv.svg` - Document CV cu rating și secțiuni
- **udemy** → `images/udemy.svg` - Platformă de cursuri cu video player
- **badge** → `images/badge.svg` - Insignă achievement aurie cu stele
- **chatgpt** → `images/chatgpt.svg` - AI assistant cu chat bubbles

## Implementation

Funcția `getProductImageUrl()` din `app.js` verifică mai întâi dacă există un SVG custom pentru produs:

```javascript
function getProductImageUrl(product) {
    // Use local SVG files for specific products
    const productSVGs = {
        'tricou': 'images/tricou.svg',
        'sticker': 'images/sticker.svg',
        // ... etc
    };
    
    // Check if we have a custom SVG for this product
    if (productSVGs[product.id]) {
        return productSVGs[product.id];
    }
    
    // Fallback to category-based Unsplash images
    // ...
}
```

## Benefits

1. **Consistent Branding** - Toate SVG-urile au stil uniform GT
2. **Fast Loading** - SVG-urile sunt mici și se încarcă instant
3. **Scalable** - Vectorii arată perfect la orice dimensiune
4. **No Dependencies** - Nu depindem de servicii externe pentru imaginile principale
5. **Custom Design** - Fiecare imagine reflectă exact produsul specific

## Future Improvements

- Adăugarea de animații CSS pentru SVG-uri
- Implementarea unui sistem de teme (dark/light mode pentru SVG-uri)
- Compresie optimizată pentru SVG-uri
- Sistem de cache pentru imagini
