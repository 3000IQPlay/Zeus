package org.spongepowered.tools.obfuscation.validation;

import java.util.Collection;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.tools.obfuscation.MixinValidator;
import org.spongepowered.tools.obfuscation.interfaces.IMixinAnnotationProcessor;
import org.spongepowered.tools.obfuscation.interfaces.IMixinValidator;
import org.spongepowered.tools.obfuscation.mirror.AnnotationHandle;
import org.spongepowered.tools.obfuscation.mirror.TypeHandle;
import org.spongepowered.tools.obfuscation.mirror.TypeUtils;

public class TargetValidator extends MixinValidator {
  public TargetValidator(IMixinAnnotationProcessor ap) {
    super(ap, IMixinValidator.ValidationPass.LATE);
  }
  
  public boolean validate(TypeElement mixin, AnnotationHandle annotation, Collection<TypeHandle> targets) {
    if ("true".equalsIgnoreCase(this.options.getOption("disableTargetValidator")))
      return true; 
    if (mixin.getKind() == ElementKind.INTERFACE) {
      validateInterfaceMixin(mixin, targets);
    } else {
      validateClassMixin(mixin, targets);
    } 
    return true;
  }
  
  private void validateInterfaceMixin(TypeElement mixin, Collection<TypeHandle> targets) {
    int i;
    boolean containsNonAccessorMethod = false;
    for (Element element : mixin.getEnclosedElements()) {
      if (element.getKind() == ElementKind.METHOD) {
        boolean isAccessor = AnnotationHandle.of(element, Accessor.class).exists();
        boolean isInvoker = AnnotationHandle.of(element, Invoker.class).exists();
        i = containsNonAccessorMethod | ((!isAccessor && !isInvoker) ? 1 : 0);
      } 
    } 
    if (i == 0)
      return; 
    for (TypeHandle target : targets) {
      TypeElement targetType = target.getElement();
      if (targetType != null && targetType.getKind() != ElementKind.INTERFACE)
        error("Targetted type '" + target + " of " + mixin + " is not an interface", mixin); 
    } 
  }
  
  private void validateClassMixin(TypeElement mixin, Collection<TypeHandle> targets) {
    TypeMirror superClass = mixin.getSuperclass();
    for (TypeHandle target : targets) {
      TypeMirror targetType = target.getType();
      if (targetType != null && !validateSuperClass(targetType, superClass))
        error("Superclass " + superClass + " of " + mixin + " was not found in the hierarchy of target class " + targetType, mixin); 
    } 
  }
  
  private boolean validateSuperClass(TypeMirror targetType, TypeMirror superClass) {
    if (TypeUtils.isAssignable(this.processingEnv, targetType, superClass))
      return true; 
    return validateSuperClassRecursive(targetType, superClass);
  }
  
  private boolean validateSuperClassRecursive(TypeMirror targetType, TypeMirror superClass) {
    if (!(targetType instanceof DeclaredType))
      return false; 
    if (TypeUtils.isAssignable(this.processingEnv, targetType, superClass))
      return true; 
    TypeElement targetElement = (TypeElement)((DeclaredType)targetType).asElement();
    TypeMirror targetSuper = targetElement.getSuperclass();
    if (targetSuper.getKind() == TypeKind.NONE)
      return false; 
    if (checkMixinsFor(targetSuper, superClass))
      return true; 
    return validateSuperClassRecursive(targetSuper, superClass);
  }
  
  private boolean checkMixinsFor(TypeMirror targetType, TypeMirror superClass) {
    for (TypeMirror mixinType : getMixinsTargeting(targetType)) {
      if (TypeUtils.isAssignable(this.processingEnv, mixinType, superClass))
        return true; 
    } 
    return false;
  }
}
