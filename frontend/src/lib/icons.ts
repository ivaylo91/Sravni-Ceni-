import {
  faAppleWhole, faBaby, faBacon, faBreadSlice, faCarrot, faCheese, faCookie, faDrumstickBite,
  faEgg, faFish, faJar, faMugHot, faPills, faSoap, faTag, faWheatAwn, faWineBottle,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons'

/** Иконите на групите категории идват от базата като имена от FontAwesome. */
const GROUP_ICONS: Record<string, IconDefinition> = {
  'bread-slice': faBreadSlice,
  cheese: faCheese,
  'drumstick-bite': faDrumstickBite,
  bacon: faBacon,
  fish: faFish,
  egg: faEgg,
  'wheat-awn': faWheatAwn,
  jar: faJar,
  'apple-whole': faAppleWhole,
  carrot: faCarrot,
  baby: faBaby,
  cookie: faCookie,
  'mug-hot': faMugHot,
  'wine-bottle': faWineBottle,
  soap: faSoap,
  pills: faPills,
}

export const groupIcon = (name?: string) => (name && GROUP_ICONS[name]) || faTag
