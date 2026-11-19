// Cola por conversación: un mensaje a la vez por chat (los turnos de chats distintos corren en paralelo).

export class ColaPorClave {
  private readonly colas = new Map<string, Promise<unknown>>();

  encolar<T>(clave: string, tarea: () => Promise<T>): Promise<T> {
    const anterior = this.colas.get(clave) ?? Promise.resolve();
    const actual = anterior.catch(() => undefined).then(tarea);
    const cola = actual.catch(() => undefined);
    this.colas.set(clave, cola);
    void cola.then(() => {
      if (this.colas.get(clave) === cola) this.colas.delete(clave);
    });
    return actual;
  }

  enCurso(clave: string): boolean {
    return this.colas.has(clave);
  }
}
