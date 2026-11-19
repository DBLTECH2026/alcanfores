/** Une clases condicionales: cx('btn', activo && 'btn-activo') */
export function cx(...clases: Array<string | false | null | undefined | 0>): string {
  return clases.filter(Boolean).join(' ');
}
