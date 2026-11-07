-- COG-003. Ejecutar una sola vez en SQL Editor después de revisar esta entrega.
-- No cambia asignaciones ni códigos concedidos. Añade control de concurrencia.
-- La RPC anterior se conserva para uso interno; Android COG-002 no la utiliza.
BEGIN;
CREATE FUNCTION public.configurar_permisos_cargo_verificado_app(
 p_cargo_id uuid, p_codigos text[], p_codigos_esperados text[]
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE actuales text[]; esperados text[];
BEGIN
 IF NOT COALESCE(public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL),false)
 THEN RAISE EXCEPTION 'Requiere administración nacional' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.cargos WHERE id=p_cargo_id AND activo FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cargo activo no encontrado'; END IF;
 IF p_codigos_esperados IS NULL OR EXISTS(SELECT 1 FROM unnest(p_codigos_esperados) c WHERE c IS NULL)
 THEN RAISE EXCEPTION 'Debe recargar los permisos del cargo antes de guardar'; END IF;
 SELECT COALESCE(array_agg(DISTINCT p.codigo::text ORDER BY p.codigo::text),ARRAY[]::text[]) INTO actuales
 FROM public.cargos_permisos cp JOIN public.permisos p ON p.id=cp.permiso_id WHERE cp.cargo_id=p_cargo_id;
 SELECT COALESCE(array_agg(c ORDER BY c),ARRAY[]::text[]) INTO esperados
 FROM (SELECT DISTINCT unnest(p_codigos_esperados) c) s;
 IF actuales IS DISTINCT FROM esperados THEN
  RAISE EXCEPTION 'Los permisos cambiaron mientras editaba. Actualice y revise la nueva selección';
 END IF;
 -- Reutiliza validación del cargo propio, nivel, códigos y auditoría existentes.
 RETURN public.configurar_permisos_cargo_app(p_cargo_id,p_codigos);
END $function$;
REVOKE ALL ON FUNCTION public.configurar_permisos_cargo_verificado_app(uuid,text[],text[]) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.configurar_permisos_cargo_verificado_app(uuid,text[],text[]) TO authenticated;
-- Evita eludir la comprobación mediante la firma antigua expuesta por PostgREST.
REVOKE EXECUTE ON FUNCTION public.configurar_permisos_cargo_app(uuid,text[]) FROM PUBLIC,anon,authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
