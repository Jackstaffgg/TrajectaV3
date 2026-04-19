import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-lg text-sm font-semibold tracking-[0.3px] transition-[opacity,box-shadow] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        default:
          "rounded-[86px] border border-white/20 bg-[hsl(var(--accent))] text-white shadow-[inset_0_1px_0_0_rgba(255,255,255,0.16),0_10px_22px_rgba(0,0,0,0.34)] hover:opacity-95 hover:shadow-[inset_0_1px_0_0_rgba(255,255,255,0.18),0_0_0_1px_rgba(255,255,255,0.22),0_0_22px_hsla(0,100%,69%,0.38),0_14px_28px_rgba(0,0,0,0.42)]",
        ghost:
          "rounded-[86px] border border-white/10 bg-transparent text-[hsl(var(--muted-foreground))] shadow-[inset_0_1px_0_0_rgba(255,255,255,0.06)] hover:opacity-70 hover:text-foreground",
        outline:
          "border border-white/10 bg-[rgba(16,17,17,0.75)] text-foreground shadow-[inset_0_1px_0_0_rgba(255,255,255,0.05)] hover:opacity-80",
        destructive:
          "rounded-[86px] border border-red-300/30 bg-red-500/90 text-white shadow-[inset_0_1px_0_0_rgba(255,255,255,0.16),0_10px_22px_rgba(0,0,0,0.34)] hover:opacity-90"
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-8 rounded-md px-3 text-xs",
        lg: "h-11 px-6"
      }
    },
    defaultVariants: {
      variant: "default",
      size: "default"
    }
  }
);

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  };

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);

Button.displayName = "Button";

export { Button, buttonVariants };
