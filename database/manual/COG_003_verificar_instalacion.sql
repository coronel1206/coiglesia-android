-- Solo lectura. No ejecuta cambios de permisos sobre cargos reales.
SELECT p.proname AS funcion,
 pg_get_function_identity_arguments(p.oid) AS argumentos,
 p.prosecdef AS security_definer,
 p.proconfig AS configuracion,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anon_puede_ejecutar,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_puede_ejecutar
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.proname IN (
 'configurar_permisos_cargo_app','configurar_permisos_cargo_verificado_app','permisos_cargo_app'
) ORDER BY p.proname;
