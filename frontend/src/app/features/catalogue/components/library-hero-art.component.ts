import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Illustration décorative du bandeau d'accueil (livres empilés, lampe, tablette, plante), dessinée en SVG
 * en ligne : aucune image externe (CSP). Masquée aux technologies d'assistance.
 */
@Component({
  selector: 'app-library-hero-art',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block', 'aria-hidden': 'true' },
  template: `
    <svg viewBox="0 0 460 340" class="h-auto w-full drop-shadow-xl" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="hero-glow" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#fde68a" stop-opacity="0.9" />
          <stop offset="1" stop-color="#fde68a" stop-opacity="0" />
        </linearGradient>
        <linearGradient id="hero-screen" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#eef2ff" />
          <stop offset="1" stop-color="#e0f2fe" />
        </linearGradient>
      </defs>

      <!-- halo et décor -->
      <circle cx="250" cy="170" r="150" fill="#ffffff" opacity="0.45" />
      <circle cx="250" cy="170" r="105" fill="#ffffff" opacity="0.35" />
      <path d="M60 70l4 10 10 4-10 4-4 10-4-10-10-4 10-4z" fill="#f97316" opacity="0.8" />
      <path d="M408 40l3 7 7 3-7 3-3 7-3-7-7-3 7-3z" fill="#6366f1" opacity="0.7" />
      <circle cx="420" cy="140" r="5" fill="#14b8a6" opacity="0.7" />
      <circle cx="90" cy="150" r="4" fill="#8b5cf6" opacity="0.6" />

      <!-- lampe -->
      <path d="M330 50 L300 140 L420 140 Z" fill="url(#hero-glow)" opacity="0.7" />
      <path d="M396 300 L396 120 Q396 70 350 62" stroke="#312e81" stroke-width="7" fill="none" stroke-linecap="round" />
      <path d="M318 40 Q345 18 372 44 L360 62 L330 62 Z" fill="#f97316" />
      <ellipse cx="345" cy="62" rx="16" ry="4" fill="#fdba74" />
      <rect x="372" y="296" width="48" height="10" rx="5" fill="#312e81" />

      <!-- plante -->
      <path d="M52 300 Q40 250 70 220" stroke="#0f766e" stroke-width="4" fill="none" stroke-linecap="round" />
      <path d="M70 220 Q90 214 92 232 Q76 240 70 220Z" fill="#14b8a6" />
      <path d="M58 255 Q34 240 40 222 Q60 230 58 255Z" fill="#2dd4bf" />
      <path d="M62 240 Q86 238 88 256 Q70 262 62 240Z" fill="#0d9488" />
      <path d="M36 296 h40 l-6 30 h-28 z" fill="#c7d2fe" />

      <!-- pile de livres couchés -->
      <g>
        <rect x="96" y="276" width="190" height="30" rx="5" fill="#4338ca" />
        <rect x="104" y="281" width="176" height="20" rx="3" fill="#eef2ff" />
        <rect x="96" y="276" width="14" height="30" rx="3" fill="#312e81" />
        <rect x="112" y="246" width="170" height="30" rx="5" fill="#f97316" />
        <rect x="120" y="251" width="156" height="20" rx="3" fill="#fff7ed" />
        <rect x="112" y="246" width="14" height="30" rx="3" fill="#c2410c" />
        <rect x="104" y="216" width="160" height="30" rx="5" fill="#0d9488" />
        <rect x="112" y="221" width="146" height="20" rx="3" fill="#f0fdfa" />
        <rect x="104" y="216" width="14" height="30" rx="3" fill="#0f766e" />
        <path d="M150 226 h70 M150 232 h50" stroke="#99f6e4" stroke-width="3" stroke-linecap="round" />
      </g>

      <!-- livres debout -->
      <g>
        <rect x="150" y="128" width="30" height="88" rx="4" fill="#7c3aed" />
        <rect x="150" y="146" width="30" height="6" fill="#c4b5fd" />
        <rect x="150" y="190" width="30" height="6" fill="#c4b5fd" />
        <rect x="182" y="110" width="34" height="106" rx="4" fill="#1e3a8a" />
        <rect x="190" y="124" width="18" height="30" rx="3" fill="#93c5fd" />
        <rect x="218" y="138" width="26" height="78" rx="4" fill="#e11d48" />
        <rect x="218" y="196" width="26" height="5" fill="#fecdd3" />
        <rect x="248" y="122" width="30" height="94" rx="4" transform="rotate(12 263 216)" fill="#f59e0b" />
        <rect x="252" y="140" width="22" height="5" transform="rotate(12 263 216)" fill="#fef3c7" />
      </g>

      <!-- tablette -->
      <g transform="rotate(-8 330 240)">
        <rect x="282" y="182" width="104" height="126" rx="14" fill="#1e1b4b" />
        <rect x="290" y="192" width="88" height="106" rx="8" fill="url(#hero-screen)" />
        <rect x="300" y="204" width="30" height="42" rx="4" fill="#6366f1" />
        <rect x="336" y="206" width="34" height="5" rx="2.5" fill="#a5b4fc" />
        <rect x="336" y="216" width="26" height="5" rx="2.5" fill="#c7d2fe" />
        <rect x="336" y="232" width="30" height="9" rx="4.5" fill="#14b8a6" />
        <rect x="300" y="256" width="68" height="5" rx="2.5" fill="#c7d2fe" />
        <rect x="300" y="267" width="54" height="5" rx="2.5" fill="#c7d2fe" />
        <rect x="300" y="278" width="60" height="5" rx="2.5" fill="#c7d2fe" />
      </g>

      <!-- tasse -->
      <path d="M300 300 h36 v20 a12 12 0 0 1 -12 12 h-12 a12 12 0 0 1 -12 -12 z" fill="#ffffff" />
      <path d="M336 304 a8 8 0 0 1 0 14" stroke="#ffffff" stroke-width="4" fill="none" />
      <path d="M310 290 q4 -8 0 -14 M322 290 q4 -8 0 -14" stroke="#a5b4fc" stroke-width="2.5" fill="none" stroke-linecap="round" />
    </svg>
  `,
})
export class LibraryHeroArtComponent {}
