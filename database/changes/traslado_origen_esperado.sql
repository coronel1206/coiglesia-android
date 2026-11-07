-- La firma original de cuatro parámetros se conserva para versiones instaladas.
CREATE OR REPLACE FUNCTION public.trasladar_persona_app(
p_persona_id uuid,p_congregacion_destino_id uuid,p_fecha_traslado date,
p_observaciones text,p_membresia_origen_esperada_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE vigente uuid;
BEGIN
IF NOT public.tiene_acceso_app() THEN RAISE EXCEPTION 'Acceso no autorizado' USING ERRCODE='42501'; END IF;
IF p_membresia_origen_esperada_id IS NULL THEN RAISE EXCEPTION 'Consulte la membresía de origen antes de trasladar'; END IF;
PERFORM 1 FROM public.personas WHERE id=p_persona_id AND estado='activo' FOR UPDATE;
SELECT id INTO vigente FROM public.membresias_congregacionales
WHERE persona_id=p_persona_id AND estado='activa' FOR UPDATE;
IF vigente IS DISTINCT FROM p_membresia_origen_esperada_id THEN
RAISE EXCEPTION 'La membresía cambió. Actualice el historial antes de confirmar';
END IF;
RETURN public.trasladar_persona_app(p_persona_id,p_congregacion_destino_id,p_fecha_traslado,p_observaciones);
END $$;
REVOKE ALL ON FUNCTION public.trasladar_persona_app(uuid,uuid,date,text,uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.trasladar_persona_app(uuid,uuid,date,text,uuid) TO authenticated;

