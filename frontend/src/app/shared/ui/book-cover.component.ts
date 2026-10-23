import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Palettes de couverture (dégradé + teinte du filet), choisies de façon stable à partir du titre. */
const PALETTES: readonly (readonly [string, string, string])[] = [
  ['#4338ca', '#7c3aed', '#c4b5fd'],
  ['#0f766e', '#0891b2', '#99f6e4'],
  ['#c2410c', '#e11d48', '#fed7aa'],
  ['#1e3a8a', '#0369a1', '#bae6fd'],
  ['#6d28d9', '#db2777', '#f5d0fe'],
  ['#065f46', '#4d7c0f', '#d9f99d'],
  ['#9f1239', '#7c2d12', '#fecdd3'],
  ['#1f2937', '#4338ca', '#c7d2fe'],
  ['#b45309', '#a16207', '#fde68a'],
  ['#155e75', '#6d28d9', '#a5f3fc'],
];

function hash(text: string): number {
  let value = 0;
  for (let index = 0; index < text.length; index++) {
    value = (value * 31 + text.charCodeAt(index)) >>> 0;
  }
  return value;
}

/**
 * Couverture de livre générée (aucune image externe, CSP `img-src 'self' data:`) : dégradé déterministe,
 * titre et auteur. Purement décorative : le titre est déjà donné par le texte voisin.
 */
@Component({
  selector: 'app-book-cover',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block', 'aria-hidden': 'true' },
  template: `
    <div class="relative flex aspect-[2/3] w-full flex-col overflow-hidden rounded-r-lg rounded-l-sm text-white shadow-cover"
         [style.background-image]="background()">
      <span class="absolute inset-y-0 left-0 w-2 bg-black/20"></span>
      <span class="absolute inset-y-0 left-2 w-px bg-white/25"></span>
      <span class="absolute -right-6 -top-6 h-20 w-20 rounded-full opacity-30" [style.background-color]="palette()[2]"></span>
      <span class="absolute -bottom-10 -left-4 h-28 w-28 rounded-full bg-white/10"></span>
      <div class="relative flex flex-1 flex-col justify-between px-4 py-4" [class.px-5]="large()" [class.py-6]="large()">
        <span class="h-0.5 w-8 rounded" [style.background-color]="palette()[2]"></span>
        <p class="line-clamp-4 font-semibold leading-tight [text-wrap:balance]"
           [class]="large() ? 'text-2xl' : 'text-sm'">{{ title() }}</p>
        <p class="truncate text-white/80" [class]="large() ? 'text-sm' : 'text-[0.7rem]'">{{ author() }}</p>
      </div>
    </div>
  `,
})
export class BookCoverComponent {
  readonly title = input.required<string>();
  readonly author = input('');
  readonly large = input(false);

  protected readonly palette = computed(() => PALETTES[hash(this.title()) % PALETTES.length]);
  protected readonly background = computed(() => {
    const [from, to] = this.palette();
    return `linear-gradient(150deg, ${from} 0%, ${to} 100%)`;
  });
}
